package models.audit

import com.google.inject.ImplementedBy

import java.sql.Timestamp
import models.user.SidewalkUserTableDef
import models.mission.{Mission, MissionTable}
import models.utils.MyPostgresProfile.api._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import models.utils.CommonUtils.ordered
import models.utils.MyPostgresProfile
import models.validation.ValidationTaskCommentTable
import play.api.Play.current

import javax.inject.{Inject, Singleton}

case class AuditTaskComment(auditTaskCommentId: Int, auditTaskId: Int, missionId: Int, edgeId: Int, username: String,
                            ipAddress: String, gsvPanoramaId: Option[String], heading: Option[Double],
                            pitch: Option[Double], zoom: Option[Int], lat: Option[Double], lng: Option[Double],
                            timestamp: Timestamp, comment: String)
case class GenericComment(commentType: String, username: String, gsvPanoramaId: Option[String], timestamp: Timestamp, comment: String, heading: Option[Double], pitch: Option[Double], zoom: Option[Int], labelId: Option[Int])

class AuditTaskCommentTableDef(tag: Tag) extends Table[AuditTaskComment](tag, "audit_task_comment") {
  def auditTaskCommentId: Rep[Int] = column[Int]("audit_task_comment_id", O.PrimaryKey, O.AutoInc)
  def auditTaskId: Rep[Int] = column[Int]("audit_task_id")
  def missionId: Rep[Int] = column[Int]("mission_id")
  def edgeId: Rep[Int] = column[Int]("edge_id")
  def userId: Rep[String] = column[String]("user_id")
  def ipAddress: Rep[String] = column[String]("ip_address")
  def gsvPanoramaId: Rep[Option[String]] = column[Option[String]]("gsv_panorama_id")
  def heading: Rep[Option[Double]] = column[Option[Double]]("heading")
  def pitch: Rep[Option[Double]] = column[Option[Double]]("pitch")
  def zoom: Rep[Option[Int]] = column[Option[Int]]("zoom")
  def lat: Rep[Option[Double]] = column[Option[Double]]("lat")
  def lng: Rep[Option[Double]] = column[Option[Double]]("lng")
  def timestamp: Rep[Timestamp] = column[Timestamp]("timestamp")
  def comment: Rep[String] = column[String]("comment")

  def * = (auditTaskCommentId, auditTaskId, missionId, edgeId, userId, ipAddress, gsvPanoramaId, heading, pitch, zoom, lat, lng, timestamp, comment) <>
    ((AuditTaskComment.apply _).tupled, AuditTaskComment.unapply)

//  def auditTask: ForeignKeyQuery[AuditTaskTable, AuditTask] =
//    foreignKey("audit_task_comment_audit_task_id_fkey", auditTaskId, TableQuery[AuditTaskTableDef])(_.auditTaskId)
//
//  def mission: ForeignKeyQuery[MissionTable, Mission] =
//    foreignKey("audit_task_comment_mission_id_fkey", missionId, TableQuery[MissionTableDef])(_.missionId)
}

@ImplementedBy(classOf[AuditTaskCommentTable])
trait AuditTaskCommentTableRepository {
  def insert(comment: AuditTaskComment): DBIO[Int]
}

@Singleton
class AuditTaskCommentTable @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends AuditTaskCommentTableRepository with HasDatabaseConfigProvider[MyPostgresProfile] {
  import profile.api._
  val auditTaskComments = TableQuery[AuditTaskCommentTableDef]
  val users = TableQuery[SidewalkUserTableDef]

//  /**
//    * Get all task records of the given user.
//    */
//  def all(username: String): Option[List[AuditTaskComment]] = {
//    val comments = (for {
//      (c, u) <- auditTaskComments.innerJoin(users).on(_.userId === _.userId).sortBy(_._1.timestamp.desc) if u.username === username
//    } yield (c.auditTaskCommentId, c.auditTaskId, c.missionId, c.edgeId, u.username, c.ipAddress, c.gsvPanoramaId,
//      c.heading, c.pitch, c.zoom, c.lat, c.lng, c.timestamp, c.comment)).list.map { c => AuditTaskComment.tupled(c) }
//
//    Some(comments)
//  }
//
  /**
    * Insert an audit_task_comment record.
    */
  def insert(comment: AuditTaskComment): DBIO[Int] = {
    (auditTaskComments returning auditTaskComments.map(_.auditTaskCommentId)) += comment
  }

//  /**
//    * Take last n comments from either audit or validation comment tables.
//    */
//  def takeRightAuditAndValidationComments(n: Integer): List[GenericComment] = {
//    val auditComments = (for {
//      (c, u) <- auditTaskComments.innerJoin(users).on(_.userId === _.userId).sortBy(_._1.timestamp.desc)
//    } yield ("audit", u.username, c.gsvPanoramaId, c.timestamp, c.comment, c.heading, c.pitch, c.zoom, (None: Option[Int]))).take(n).list.map(GenericComment.tupled(_))
//
//    val validationComments = (for {
//      (c, u) <- ValidationTaskCommentTable.validationTaskComments.innerJoin(users).on(_.userId === _.userId).sortBy(_._1.timestamp.desc)
//    } yield ("validation", u.username, c.gsvPanoramaId, c.timestamp, c.comment, c.heading, c.pitch, c.zoom, c.labelId)).take(n).list.map(c => GenericComment(c._1, c._2, Some(c._3), c._4, c._5, Some(c._6), Some(c._7), Some(c._8), Some(c._9)))
//
//    (auditComments ++ validationComments).sortBy(_.timestamp).reverse.take(n)
//  }
}
