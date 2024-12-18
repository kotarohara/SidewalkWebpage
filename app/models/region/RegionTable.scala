package models.region

import models.audit.AuditTaskTableDef
import models.street.StreetEdgeRegionTable
import models.utils.MyPostgresDriver
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
//import slick.driver.PostgresDriver.api._
//import play.api.libs.concurrent.Execution.Implicits.defaultContext
import javax.inject._
import play.api.db.slick.HasDatabaseConfigProvider
import com.google.inject.ImplementedBy

import models.utils.MyPostgresDriver.api._

import com.vividsolutions.jts.geom.MultiPolygon
//import com.vividsolutions.jts.geom.{Coordinate, MultiPolygon, Polygon}

//import java.util.UUID
//import controllers.APIBBox
//import play.api.Play.current
//import play.extras.geojson
//import play.extras.geojson.LatLng
//import scala.collection.immutable.Seq

//import scala.slick.jdbc.{GetResult, StaticQuery => Q}

//case class Region(regionId: Int, dataSource: String, name: String, deleted: Boolean)
case class Region(regionId: Int, dataSource: String, name: String, geom: MultiPolygon, deleted: Boolean)

class RegionTableDef(tag: Tag) extends Table[Region](tag, "region") {
  def regionId: Rep[Int] = column[Int]("region_id", O.PrimaryKey, O.AutoInc)
  def dataSource: Rep[String] = column[String]("data_source")
  def name: Rep[String] = column[String]("name")
  def geom: Rep[MultiPolygon] = column[MultiPolygon]("geom")
  def deleted: Rep[Boolean] = column[Boolean]("deleted")

  def * = (regionId, dataSource, name, geom, deleted) <> ((Region.apply _).tupled, Region.unapply)
}

@ImplementedBy(classOf[RegionTable])
trait RegionTableRepository {
  def getAllRegions: Future[Seq[Region]]
  def getNeighborhoodsWithUserCompletionStatus(userId: String, regionIds: List[Int]): Future[Seq[(Region, Boolean)]]
}

@Singleton
class RegionTable @Inject()(
                             protected val dbConfigProvider: DatabaseConfigProvider,
                             streetEdgeRegionTable: StreetEdgeRegionTable
                           ) extends RegionTableRepository with HasDatabaseConfigProvider[MyPostgresDriver] {
  import driver.api._

  val regions = TableQuery[RegionTableDef]
  val auditTasks = TableQuery[AuditTaskTableDef]

//  implicit val regionConverter = GetResult[Region](r => {
//    Region(r.nextInt, r.nextString, r.nextString, r.nextGeometry[MultiPolygon], r.nextBoolean)
//  })

//  case class StreetCompletion(regionId: Int, regionName: String, streetEdgeId: Int, completionCount: Int, distance: Double)
//  implicit val streetCompletionConverter = GetResult[StreetCompletion](r => {
//    StreetCompletion(r.nextInt, r.nextString, r.nextInt, r.nextInt, r.nextDouble)
//  })

//  implicit class MultiPolygonUtils(val multiPolygon: MultiPolygon) {
//    // Put MultiPolygon in geojson format, an array[array[array[latlng]]], where each array[array[latlng]] represents a
//    // single polygon. In each polygon, the first array contains the latlngs for the outer boundary of the polygon, and
//    // the remaining arrays have the latlngs for any holes in the polygon.
//    def toJSON: geojson.MultiPolygon[LatLng] = {
//      val nPolygons: Int = multiPolygon.getNumGeometries
//      val allCoordinates: Seq[Seq[Seq[geojson.LatLng]]] = (0 until nPolygons).map { polygonIndex =>
//        val currPolygon: Polygon = multiPolygon.getGeometryN(polygonIndex).asInstanceOf[Polygon]
//        val nHoles: Int = currPolygon.getNumInteriorRing
//        val outerRing: Seq[Array[Coordinate]] = Seq(currPolygon.getExteriorRing.getCoordinates)
//        val holes: Seq[Array[Coordinate]] = (0 until nHoles).map(i => currPolygon.getInteriorRingN(i).getCoordinates)
//        val coordinates: Seq[Array[Coordinate]] = outerRing ++ holes
//        coordinates.map { ring => ring.map(coord => geojson.LatLng(coord.y, coord.x)).toList }
//      }
//      geojson.MultiPolygon(allCoordinates)
//    }
//  }

//  val userCurrentRegions = TableQuery[UserCurrentRegionTableDef]

  val regionsWithoutDeleted = regions.filter(_.deleted === false)

  /**
    * Returns a list of all neighborhoods with names.
    */
  def getAllRegions: Future[Seq[Region]] = {
    db.run(regionsWithoutDeleted.result)
  }

  /**
   * Return the name of the given neighborhood.
   */
//  def neighborhoodName(regionId: Int): Option[String] = {
//    regions.filter(_.regionId === regionId).map(_.name).firstOption
//  }

  /**
    * Picks one of the regions with highest average priority out of those that the user has not completed.
    */
//  def selectAHighPriorityRegion(userId: UUID): Option[Region] = {
//    val regionsNotFinishedByUser: List[Int] = AuditTaskTable.selectIncompleteRegions(userId).toList
//
//    if (regionsNotFinishedByUser.nonEmpty) selectAHighPriorityRegionGeneric(regionsNotFinishedByUser)
//    else selectAHighPriorityRegionGeneric(regionsWithoutDeleted.map(_.regionId).list)
//  }

  /**
    * Out of the provided regions, picks one of the 5 with highest average priority across their street edges.
    */
//  def selectAHighPriorityRegionGeneric(possibleRegionIds: List[Int]): Option[Region] = {
//    val highestPriorityRegions: List[Int] =
//      StreetEdgeRegionTable.streetEdgeRegionTable
//      .filter(_.regionId inSet possibleRegionIds)
//      .innerJoin(StreetEdgePriorityTable.streetEdgePriorities).on(_.streetEdgeId === _.streetEdgeId)
//      .map { case (_region, _priority) => (_region.regionId, _priority.priority) } // select region_id, priority
//      .groupBy(_._1).map { case (_regionId, group) => (_regionId, group.map(_._2).avg) } // get avg priority by region
//      .sortBy(_._2.desc).take(5).map(_._1).list // take the 5 with highest average priority, select region_id
//
//    scala.util.Random.shuffle(highestPriorityRegions).headOption.flatMap(getRegion)
//  }

  /**
    * Get the region specified by the region id.
    */
//  def getRegion(regionId: Int): Option[Region] = {
//    regionsWithoutDeleted.filter(_.regionId === regionId).firstOption
//  }

//  def getRegionByName(regionName: String): Option[Region] = {
//    regionsWithoutDeleted.filter(_.name === regionName).firstOption
//  }

  /**
    * Get the neighborhood that is currently assigned to the user.
    */
//  def getCurrentRegion(userId: UUID): Option[Region] = {
//    val _currentRegion = for {
//      _region <- regionsWithoutDeleted
//      _userCurrRegion <- userCurrentRegions if _region.regionId === _userCurrRegion.regionId
//      if _userCurrRegion.userId === userId.toString
//    } yield _region
//
//    _currentRegion.firstOption
//  }

  /**
    * Returns a list of neighborhoods within the given bounding box.
    */
//  def getNeighborhoodsWithin(bbox: APIBBox): List[Region] = {
//    // http://postgis.net/docs/ST_MakeEnvelope.html
//    // geometry ST_MakeEnvelope(double precision xmin, double precision ymin, double precision xmax, double precision ymax, integer srid=unknown);
//    val selectNeighborhoodsQuery = Q.query[(Double, Double, Double, Double), Region](
//      """SELECT region.region_id, region.data_source, region.name, region.geom, region.deleted
//        |FROM region
//        |WHERE region.deleted = FALSE
//        |    AND ST_Within(region.geom, ST_MakeEnvelope(?,?,?,?,4326))""".stripMargin
//    )
//    selectNeighborhoodsQuery((bbox.minLng, bbox.minLat, bbox.maxLng, bbox.maxLat)).list
//  }

  /**
   * Gets regions w/ boolean noting if given user fully audited the region. If provided, filter for only given regions.
   */
  def getNeighborhoodsWithUserCompletionStatus(userId: String, regionIds: List[Int]): Future[Seq[(Region, Boolean)]] = {
    val userTasks = auditTasks.filter(a => a.completed && a.userId === userId)
    // Get regions that the user has not fully audited.
    val incompleteRegionsForUser = streetEdgeRegionTable.nonDeletedStreetEdgeRegions // FROM street_edge_region
      .joinLeft(userTasks).on(_.streetEdgeId === _.streetEdgeId) // LEFT JOIN audit_task
      .filter(_._2.isEmpty) // WHERE audit_task.audit_task_id IS NULL
      .groupBy(_._1.regionId) // GROUP BY region_id
      .map(_._1) // SELECT region_id

    // Left join regions and incomplete neighborhoods to record completion status.
    db.run(
      regionsWithoutDeleted
        .filter(_r => (_r.regionId inSet regionIds) || regionIds.isEmpty) // WHERE region_id IN regionIds
        .joinLeft(incompleteRegionsForUser).on(_.regionId === _)
        .map(x => (x._1, x._2.isEmpty)).result
    )
  }

  /**
    * Gets the region id of the neighborhood wherein the lat-lng point is located, the closest neighborhood otherwise.
    */
//  def selectRegionIdOfClosestNeighborhood(lng: Float, lat: Float): Int = {
//    val closestNeighborhoodQuery = Q.query[(Float, Float), Int](
//      """SELECT region_id
//        |FROM region
//        |WHERE deleted = FALSE
//        |ORDER BY ST_Distance(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)) ASC
//        |LIMIT 1;""".stripMargin
//    )
//    closestNeighborhoodQuery((lng, lat)).first
//  }
}
