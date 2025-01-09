package service

import scala.concurrent.{ExecutionContext, Future}
import javax.inject._
import play.api.cache._
import com.google.inject.ImplementedBy
import models.label.{LabelHistory, LabelHistoryTable, LabelHistoryTableDef, LabelTable, LabelTableDef, LabelValidation, LabelValidationTable, LabelValidationTableDef}
import models.user.UserStatTable
import models.utils.MyPostgresDriver
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import models.utils.MyPostgresDriver.api._
import slick.dbio.Effect
import slick.profile.FixedSqlAction

import java.sql.Timestamp
import java.time.Instant

@ImplementedBy(classOf[ValidationServiceImpl])
trait ValidationService {
  def countValidations: Future[Int]
  def submitLabelMapValidation(newValidation: LabelValidation): Future[Int]
}

@Singleton
class ValidationServiceImpl @Inject()(
                                  protected val dbConfigProvider: DatabaseConfigProvider,
                                  cache: CacheApi,
                                  labelValidationTable: LabelValidationTable,
                                  labelTable: LabelTable,
                                  labelHistoryTable: LabelHistoryTable,
                                  userStatTable: UserStatTable,
                                  implicit val ec: ExecutionContext
                                 ) extends ValidationService with HasDatabaseConfigProvider[MyPostgresDriver] {
  //  import driver.api._
  val validationLabels = TableQuery[LabelValidationTableDef]
  val labelsUnfiltered = TableQuery[LabelTableDef]
  val labelHistories = TableQuery[LabelHistoryTableDef]

  def countValidations: Future[Int] = {
    db.run(labelValidationTable.countValidations)
  }

  /**
   * Updates the validation counts and correctness columns in the label table given a new incoming validation.
   *
   * @param labelId label_id of the label with a new validation
   * @param newValidationResult the new validation: 1 meaning agree, 2 meaning disagree, and 3 meaning unsure
   * @param oldValidationResult the old validation if the user had validated this label in the past
   */
  def updateValidationCounts(labelId: Int, newValidationResult: Option[Int], oldValidationResult: Option[Int]): DBIO[Int] = {
    require(newValidationResult.isEmpty || List(1, 2, 3).contains(newValidationResult.get), "New validation results can only be 1, 2, or 3.")
    require(oldValidationResult.isEmpty || List(1, 2, 3).contains(oldValidationResult.get), "Old validation results can only be 1, 2, or 3.")

    labelTable.find(labelId).flatMap {
      case Some(label) =>
        // Get the validation counts that are in the database right now.
        val oldCounts: (Int, Int, Int) = (label.agreeCount, label.disagreeCount, label.unsureCount)

        // Add 1 to the correct count for the new validation. In case of delete, no match is found.
        val countsWithNewVal: (Int, Int, Int) = newValidationResult match {
          case Some(1) => (oldCounts._1 + 1, oldCounts._2, oldCounts._3)
          case Some(2) => (oldCounts._1, oldCounts._2 + 1, oldCounts._3)
          case Some(3) => (oldCounts._1, oldCounts._2, oldCounts._3 + 1)
          case _ => oldCounts
        }

        // If there was a previous validation from this user, subtract 1 for that old validation. O/w use previous result.
        val countsWithoutOldVal: (Int, Int, Int) = oldValidationResult match {
          case Some(1) => (countsWithNewVal._1 - 1, countsWithNewVal._2, countsWithNewVal._3)
          case Some(2) => (countsWithNewVal._1, countsWithNewVal._2 - 1, countsWithNewVal._3)
          case Some(3) => (countsWithNewVal._1, countsWithNewVal._2, countsWithNewVal._3 - 1)
          case _ => countsWithNewVal
        }

        // Determine whether the label is correct. Agree > disagree = correct; disagree > agree = incorrect; o/w null.
        val labelCorrect: Option[Boolean] = {
          if (countsWithoutOldVal._1 > countsWithoutOldVal._2) Some(true)
          else if (countsWithoutOldVal._2 > countsWithoutOldVal._1) Some(false)
          else None
        }

        // Update the agree_count, disagree_count, unsure_count, and correct columns in the label table.
        labelsUnfiltered
          .filter(_.labelId === labelId)
          .map(l => (l.agreeCount, l.disagreeCount, l.unsureCount, l.correct))
          .update((countsWithoutOldVal._1, countsWithoutOldVal._2, countsWithoutOldVal._3, labelCorrect))

      case None =>
        DBIO.successful(0)
    }.transactionally
  }

  /**
   * Deletes a validation in the label_validation table. Also updates validation counts in the label table.
   *
   * @param labelId
   * @param userId
   * @return Int count of rows deleted, should be either 0 or 1 because each user should have one validation per label.
   */
  private def deleteLabelValidationIfExists(labelId: Int, userId: String): DBIO[Int] = {
    labelValidationTable.getValidation(labelId, userId).flatMap {
      case Some(oldVal) =>
        for {
          historyEntryDeleted <- {
            if (oldVal.validationResult == 1) removeLabelHistoryForValidation(oldVal.labelValidationId)
            else DBIO.successful(false)
          }
          excludedUser <- userStatTable.isExcludedUser(userId)
          labeler <- labelTable.find(labelId).map(_.get.userId)
          rowsAffected <- validationLabels.filter(_.labelValidationId === oldVal.labelValidationId).delete
          _ <- {
            if (labeler != userId & !excludedUser) updateValidationCounts(labelId, None, Some(oldVal.validationResult))
            else DBIO.successful(0)
          }
        } yield {
          rowsAffected
        }
      case None => DBIO.successful(0)
    }.transactionally
  }

  /**
   * Updates the label and label_history tables appropriately when a validation is deleted (using the back button).
   *
   * If the given validation represents the most recent change to the label, undo this validation's change in the label
   * table and delete this validation. If there have been subsequent changes to the label, just delete this validation.
   * However, if the next change to the label reverses the change made by this validation, the subsequent label_history
   * entry should be deleted as well (so that the history doesn't contain a redundant entry). And if the validation did
   * not change the severity or tags, then there is nothing to remove from the label_history table.
   * .
   * @param labelValidationId
   * @return
   */
  def removeLabelHistoryForValidation(labelValidationId: Int): DBIO[Boolean] =  {
    labelHistoryTable.findByLabelValidationId(labelValidationId).map(_.headOption).flatMap {
      case Some(historyEntry) =>
        labelHistoryTable.findByLabelId(historyEntry.labelId).map(_.sortBy(_.editTime.getTime)).flatMap { fullHistory =>
          // If the given validation represents the most recent change to the label, undo this validation's change in
          // the label table and delete this validation from the label_history table.
          if (fullHistory.indexWhere(_.labelHistoryId == historyEntry.labelHistoryId) == fullHistory.length - 1) {
            val correctData: LabelHistory = fullHistory(fullHistory.length - 2)
            val labelToUpdateQuery = labelsUnfiltered.filter(_.labelId === historyEntry.labelId)
            labelToUpdateQuery.map(l => (l.severity, l.tags)).update((correctData.severity, correctData.tags))
            labelHistories.filter(_.labelValidationId === labelValidationId).delete.map(_ > 0)
          } else {
            // If the next history entry reverses this one, we can update the label table and delete both entries.
            val thisEntryIdx: Int = fullHistory.indexWhere(_.labelValidationId == Some(labelValidationId))
            if (fullHistory(thisEntryIdx - 1).severity == fullHistory(thisEntryIdx + 1).severity
              && fullHistory(thisEntryIdx - 1).tags == fullHistory(thisEntryIdx + 1).tags) {
              for {
                delete1 <- labelHistories.filter(_.labelValidationId === labelValidationId).delete
                delete2 <- labelHistories.filter(_.labelValidationId === fullHistory(thisEntryIdx + 1).labelValidationId).delete
              } yield delete1 > 0 && delete2 > 0
            } else {
              labelHistories.filter(_.labelValidationId === labelValidationId).delete.map(_ > 0)
            }
          }
        }
      case None =>
        // No label_history entry to delete (this would happen if the validation didn't change severity or tags).
        DBIO.successful(false)
    }.transactionally
  }

  /**
   * Inserts into the label_validation table. Updates severity, tags, & validation counts in the label table.
   *
   * @return The label_validation_id of the inserted/updated validation.
   */
  def insert(labelVal: LabelValidation): DBIO[Int] = {
    for {
      isExcludedUser <- userStatTable.isExcludedUser(labelVal.userId)
      userThatAppliedLabel <- labelsUnfiltered.filter(_.labelId === labelVal.labelId).map(_.userId).result.head
      _ <- {
        if (userThatAppliedLabel != labelVal.userId & !isExcludedUser)
          updateValidationCounts(labelVal.labelId, Some(labelVal.validationResult), None)
        else DBIO.successful(0)
      }
      newValId <- (validationLabels returning validationLabels.map(_.labelValidationId)) += labelVal
    } yield newValId
  }.transactionally

  /**
   * Updates severity and tags in the label table and saves the change in the label_history table. Called from Validate.
   *
   * @param labelId
   * @param severity
   * @param tags
   * @param userId
   * @return Int count of rows updated, either 0 or 1 because labelId is a primary key.
   */
  def updateAndSaveHistory(labelId: Int, severity: Option[Int], tags: List[String], userId: String, source: String, labelValidationId: Int): DBIO[Int] = {
    val labelToUpdateQuery = labelsUnfiltered.filter(_.labelId === labelId)
    labelToUpdateQuery.result.headOption.flatMap {
      case Some(labelToUpdate) =>
        // TODO add tag cleaning back in, skipping for now so I can test everything else.
//        val cleanedTags: List[String] = TagTable.cleanTagList(tags, labelToUpdate.labelTypeId)
        val cleanedTags: List[String] = tags

        // If there is an actual change to the label, update it and add to the label_history table. O/w update nothing.
        if (labelToUpdate.severity != severity || labelToUpdate.tags.toSet != cleanedTags.toSet) {
          labelHistoryTable.insert(LabelHistory(0, labelId, severity, cleanedTags, userId, new Timestamp(Instant.now.toEpochMilli), source, Some(labelValidationId)))
          labelToUpdateQuery.map(l => (l.severity, l.tags)).update((severity, cleanedTags))
        } else {
          DBIO.successful(0)
        }
      case None => DBIO.successful(0)
    }.transactionally
  }

  def submitLabelMapValidation(newVal: LabelValidation): Future[Int] = {
    db.run((for {
      // Delete any previous validation of this label from the same user.
      _ <- deleteLabelValidationIfExists(newVal.labelId, newVal.userId)

      // Insert a label_validation entry for this label.
      newValId: Int <- insert(newVal)

      // Now we update the severity and tags in the label table if something changed.
      _ <- updateAndSaveHistory(newVal.labelId, newVal.newSeverity, newVal.newTags, newVal.userId, newVal.source, newValId)

      // For the user whose labels has been validated, update their accuracy in the user_stat table.
      // TODO this doesn't need to be part of the transaction. It can be run async after everything else is done.
      usersValidated: Seq[String] <- labelValidationTable.usersValidated(Seq(newVal.labelId))
      _ <- userStatTable.updateAccuracy(usersValidated)
    } yield {
      newValId
    }).transactionally)
  }
}
