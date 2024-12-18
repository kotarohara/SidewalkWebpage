package controllers

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, SessionAuthenticator}
//import controllers.headers.ProvidesHeader
import formats.json.SurveySubmissionFormats._
import models.survey._
import models.user._
import models.mission.MissionTable
//import models.user.{WebpageActivityTable}
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.mvc._

import scala.collection.immutable.Seq
import scala.concurrent.Future

@Singleton
class SurveyController @Inject() (val messagesApi: MessagesApi, val env: Environment[SidewalkUserWithRole, CookieAuthenticator])
  extends Silhouette[SidewalkUserWithRole, CookieAuthenticator] {

  /**
   * Submit the data associated with a completed survey.
   */
//  def postSurvey = UserAwareAction.async(BodyParsers.parse.json) { implicit request =>
//    var submission = request.body.validate[Seq[SurveySingleSubmission]]
//
//    submission.fold(
//      errors => {
//        Future.successful(BadRequest(Json.obj("status" -> "Error", "message" -> JsError.toFlatJson(errors))))
//      },
//      submission => {
//
//        val userId: String = request.identity match {
//          case Some(user) => user.userId.toString
//          case None =>
//            Logger.warn("User without a user_id completed a survey, but every user should have a user_id.")
//            val user: Option[DBUser] = UserTable.find("anonymous")
//            user.get.userId.toString
//        }
//
//        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
//
//        //this will log when a user submits a survey response.
//        val ipAddress: String = request.remoteAddress
//        webpageActivityService.insert(WebpageActivity(0, userId, ipAddress, "SurveySubmit", timestamp))
//
//        val numMissionsCompleted: Int = MissionTable.countCompletedMissions(UUID.fromString(userId), includeOnboarding = false, includeSkipped = true)
//
//        val allSurveyQuestions: List[SurveyQuestion] = SurveyQuestionTable.listAll
//        val allSurveyQuestionIds: List[Int] = allSurveyQuestions.map(_.surveyQuestionId)
//        val answeredQuestionIds: Seq[Int] = submission.map(_.surveyQuestionId.toInt)
//        val unansweredQuestionIds: List[Int] = allSurveyQuestionIds diff answeredQuestionIds
//        // Iterate over all the questions and check if there is a submission attribute matching question id.
//        // Add the associated submission to the user_submission tables for that question.
//
//
//        submission.foreach{ q =>
//          val questionId: Int = q.surveyQuestionId.toInt
//          val temp_question: Option[SurveyQuestion] = SurveyQuestionTable.getQuestionById(questionId)
//          temp_question match{
//            case Some(question) =>
//              if (question.surveyInputType != "free-text-feedback") {
//                val userSurveyOptionSubmission = UserSurveyOptionSubmission(0, userId, question.surveyQuestionId, Some(q.answerText.toInt), timestamp, numMissionsCompleted)
//                val userSurveyOptionSubmissionId: Int = UserSurveyOptionSubmissionTable.save(userSurveyOptionSubmission)
//              }
//              else {
//                val userSurveyTextSubmission = UserSurveyTextSubmission(0, userId, question.surveyQuestionId, Some(q.answerText), timestamp, numMissionsCompleted)
//                val userSurveyTextSubmissionId: Int = UserSurveyTextSubmissionTable.save(userSurveyTextSubmission)
//              }
//            case None =>
//              None
//          }
//        }
//        unansweredQuestionIds.foreach{ questionId =>
//          val temp_question: Option[SurveyQuestion] = SurveyQuestionTable.getQuestionById(questionId)
//          temp_question match{
//            case Some(question)=>
//              if(question.surveyInputType != "free-text-feedback"){
//                val userSurveyOptionSubmission = UserSurveyOptionSubmission(0, userId, question.surveyQuestionId, None, timestamp, numMissionsCompleted)
//                val userSurveyOptionSubmissionId: Int = UserSurveyOptionSubmissionTable.save(userSurveyOptionSubmission)
//              }
//              else{
//                val userSurveyTextSubmission = UserSurveyTextSubmission(0, userId, question.surveyQuestionId, None, timestamp, numMissionsCompleted)
//                val userSurveyTextSubmissionId: Int = UserSurveyTextSubmissionTable.save(userSurveyTextSubmission)
//              }
//            case None =>
//              None
//          }
//        }
//        Future.successful(Ok(Json.obj("survey_success" -> "True")))
//      }
//    )
//  }

  /**
   * Determine whether or not a survey should be shown to the signed in user.
   */
//  def shouldDisplaySurvey = UserAwareAction.async { implicit request =>
//    request.identity match {
//      case Some(user) =>
//        val userId: UUID = user.userId
//        val cityId: String = Play.configuration.getString("city-id").get
//
//        // The survey will show exactly once, in the middle of the 2nd mission.
//        val numMissionsBeforeSurvey = 1
//        val surveyShown: Boolean = WebpageActivityTable.findUserActivity("SurveyShown", userId).nonEmpty
//        val displaySurvey: Boolean =
//          MissionTable.countCompletedMissions(userId, includeOnboarding = false, includeSkipped = true) == numMissionsBeforeSurvey &&
//            !surveyShown &&
//            cityId != "crowdstudy" // Crowdstudy has pre- and post- questionnaires with similar questions.
//
//        //maps displaymodal to true in the future.
//        Future.successful(Ok(Json.obj("displayModal" -> displaySurvey)))
//
//      case None => Future.successful(Redirect(s"/anonSignUp?url=/survey/display"))
//    }
//  }
}
