package models.amt

import java.sql.Timestamp

import models.utils.MyPostgresDriver.simple._
import play.api.Play.current

case class AMTAssignment(amtAssignmentId: Int, hitId: String, assignmentId: String, assignmentStart: Timestamp, assignmentEnd: Option[Timestamp], workerId: String, confirmationCode: Option[String])

/**
 *
 */
class AMTAssignmentTable(tag: Tag) extends Table[AMTAssignment](tag, Some("sidewalk"), "amt_assignment") {
  def amtAssignmentId = column[Int]("amt_assignment_id", O.PrimaryKey, O.AutoInc)
  def hitId = column[String]("hit_id", O.NotNull)
  def assignmentId = column[String]("assignment_id", O.NotNull)
  def assignmentStart = column[Timestamp]("assignment_start", O.NotNull)
  def assignmentEnd = column[Option[Timestamp]]("assignment_end")
  def workerId = column[String]("turker_id", O.NotNull)
  def confirmationCode = column[Option[String]]("confirmation_code")

  def * = (amtAssignmentId, hitId, assignmentId, assignmentStart, assignmentEnd, workerId, confirmationCode) <> ((AMTAssignment.apply _).tupled, AMTAssignment.unapply)
}

/**
 * Data access object for the label table
 */
object AMTAssignmentTable {
  val db = play.api.db.slick.DB
  val amtAssignments = TableQuery[AMTAssignmentTable]

  def save(asg: AMTAssignment): Int = db.withTransaction { implicit session =>
    val asgId: Int =
      (amtAssignments returning amtAssignments.map(_.amtAssignmentId)) += asg
    asgId
  }
  def getConfirmationCode(workerId: String, assignmentId: String): String = db.withTransaction { implicit session =>
    amtAssignments.filter( x => x.workerId === workerId && x.assignmentId === assignmentId).sortBy(_.assignmentStart.desc).map(_.confirmationCode).list.head.getOrElse("")
  }
  def getMostRecentAssignmentId(workerId: String): String = db.withTransaction { implicit session =>
    amtAssignments.filter( x => x.workerId === workerId).sortBy(_.assignmentStart.desc).map(_.assignmentId).list.head
  }
}

