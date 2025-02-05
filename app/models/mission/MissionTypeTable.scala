package models.mission

import com.google.inject.ImplementedBy
import models.utils.MyPostgresProfile
import models.utils.MyPostgresProfile.api._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.Play.current

import javax.inject.{Inject, Singleton}

case class MissionType(missionTypeId: Int, missionType: String)

class MissionTypeTableDef(tag: slick.lifted.Tag) extends Table[MissionType](tag, "mission_type") {
  def missionTypeId: Rep[Int] = column[Int]("mission_type_id", O.PrimaryKey, O.AutoInc)
  def missionType: Rep[String] = column[String]("mission_type")

  def * = (missionTypeId, missionType) <> ((MissionType.apply _).tupled, MissionType.unapply)
}

/**
 * Companion object with constants that are shared throughout codebase.
 */
object MissionTypeTable {
  val onboardingTypes: List[String] = List("auditOnboarding", "validationOnboarding")
  val missionTypeToId: Map[String, Int] = Map("auditOnboarding" -> 1, "audit" -> 2, "validationOnboarding" -> 3, "validation" -> 4, "cvGroundTruth" -> 5, "labelmapValidation" -> 7)
  val missionTypeIdToMissionType: Map[Int, String] = missionTypeToId.map(_.swap)
}

@ImplementedBy(classOf[MissionTypeTable])
trait MissionTypeTableRepository {
}

@Singleton
class MissionTypeTable @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends MissionTypeTableRepository with HasDatabaseConfigProvider[MyPostgresProfile] {
  import profile.api._
  val missionTypes = TableQuery[MissionTypeTableDef]

//  val onboardingTypeIds: List[Int] = {
//    missionTypes.filter(_.missionType inSet onboardingTypes).map(_.missionTypeId).list
//  }
//
//  /**
//    * Gets the mission type id from the mission type name.
//    *
//    * @param missionType    Name field for this mission type
//    * @return               ID associated with this mission type
//    */
//  def missionTypeToId(missionType: String): Int = {
//    missionTypes.filter(_.missionType === missionType).map(_.missionTypeId).first
//  }
//
//  /**
//    * Gets the mission type name from the mission type id.
//    *
//    * @param missionTypeId  ID associated with this mission type
//    * @return               Name field for this mission type
//    */
//  def missionTypeIdToMissionType(missionTypeId: Int): String = {
//    missionTypes.filter(_.missionTypeId === missionTypeId).map(_.missionType).first
//  }
}
