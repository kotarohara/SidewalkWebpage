package service.region

import scala.concurrent.{ExecutionContext, Future}
import javax.inject._
import com.google.inject.ImplementedBy
import models.region.{NamedRegionCompletion, Region, RegionCompletion, RegionCompletionTable, RegionTable}
import models.street.{StreetEdgePriorityTableDef, StreetEdgeRegionTableDef, StreetEdgeTable}
import models.utils.MyPostgresDriver
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import models.utils.MyPostgresDriver.api._

@ImplementedBy(classOf[RegionServiceImpl])
trait RegionService {
  def getAllRegions: Future[Seq[Region]]
  def getNeighborhoodsWithUserCompletionStatus(userId: String, regionIds: Seq[Int]): Future[Seq[(Region, Boolean)]]
  def selectAllNamedNeighborhoodCompletions(regionIds: Seq[Int]): Future[Seq[NamedRegionCompletion]]
  def initializeRegionCompletionTable: Future[Int]
}

@Singleton
class RegionServiceImpl @Inject()(
                                   protected val dbConfigProvider: DatabaseConfigProvider,
                                   regionTable: RegionTable,
                                   regionCompletionTable: RegionCompletionTable,
                                   streetEdgeTable: StreetEdgeTable,
                                   implicit val ec: ExecutionContext
                                 ) extends RegionService with HasDatabaseConfigProvider[MyPostgresDriver] {
//  import driver.api._

  def getAllRegions: Future[Seq[Region]] = {
    regionTable.getAllRegions
  }

  def getNeighborhoodsWithUserCompletionStatus(userId: String, regionIds: Seq[Int]): Future[Seq[(Region, Boolean)]] = {
    regionTable.getNeighborhoodsWithUserCompletionStatus(userId, regionIds)
  }

  def selectAllNamedNeighborhoodCompletions(regionIds: Seq[Int]): Future[Seq[NamedRegionCompletion]] = {
    db.run(regionCompletionTable.selectAllNamedNeighborhoodCompletions(regionIds))
  }


  val regionCompletions = regionCompletionTable.regionCompletions
  val streetEdgeRegion = TableQuery[StreetEdgeRegionTableDef]
  val streetEdgePriorities = TableQuery[StreetEdgePriorityTableDef]

  /**
   * If the region_completion table is empty, initializes it with the total and audited distance for each region.
   *
   * @return The number of rows inserted into the region_completion table.
   */
  def initializeRegionCompletionTable: Future[Int] = {
    val tableInitAction = for {
      count: Int <- regionCompletionTable.count
      numInserted: Int <- if (count == 0) {
        val streetsInRegion = for {
          _edgeRegion <- streetEdgeRegion
          _edges <- streetEdgeTable.streetEdgesWithoutDeleted if _edges.streetEdgeId === _edgeRegion.streetEdgeId
          _edgePriority <- streetEdgePriorities if _edges.streetEdgeId === _edgePriority.streetEdgeId
        } yield (_edgeRegion.regionId, _edges.geom.transform(26918), _edgePriority.priority < 1.0)

        // Get region_id, total_distance, audited_distance for each region.
        val regionsQuery = streetsInRegion.groupBy(_._1).map { case (regionId, group) =>
          (
            regionId,
            group.map(_._2.length).sum.getOrElse(0.0F),
            group.map(s => Case.If(s._3).Then(s._2.length).Else(0.0F)).sum.getOrElse(0.0F)
          )
        }

        for {
          regions <- regionsQuery.result
          insertCount <- (regionCompletions ++= regions.map(r => RegionCompletion(r._1, r._2.toDouble, r._3.toDouble))).map(_.getOrElse(0))
        } yield insertCount
      } else {
        DBIO.successful(0) // If the table is already initialized, 0 rows inserted.
      }
    } yield numInserted

    db.run(tableInitAction.transactionally)
  }
}