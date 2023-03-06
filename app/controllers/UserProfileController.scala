package controllers

import javax.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import com.vividsolutions.jts.geom.Coordinate
import controllers.headers.ProvidesHeader
import formats.json.LabelFormat.labelMetadataUserDashToJson
import models.audit.{AuditTaskTable, StreetEdgeWithAuditStatus}
import models.user.UserOrgTable
import models.label.{LabelLocation, LabelTable, LabelValidationTable}
import models.user.{User, WebpageActivity, WebpageActivityTable}
import models.utils.CommonUtils.METERS_TO_MILES
import play.api.libs.json.{JsObject, JsValue, Json}
import play.extras.geojson
import play.api.i18n.Messages

import scala.concurrent.Future

/**
 * Holds the HTTP requests associated with the user dashboard.
 *
 * @param env The Silhouette environment.
 */
class UserProfileController @Inject() (implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader  {

  /**
   * Loads the user dashboard page.
   */
  def userProfile = UserAwareAction.async { implicit request =>
    // If they are an anonymous user, send them to the sign in page.
    if (request.identity.isEmpty || request.identity.get.role.getOrElse("") == "Anonymous") {
      Future.successful(Redirect(s"/signIn?url=/"))
    } else {
      val user: User = request.identity.get
      val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
      val ipAddress: String = request.remoteAddress
      WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_UserDashboard", timestamp))
      // Get distance audited by the user. Convert meters to km if using metric system, to miles if using IS.
      val auditedDistance: Float = {
        if (Messages("measurement.system") == "metric") AuditTaskTable.getDistanceAudited(user.userId) / 1000F
        else AuditTaskTable.getDistanceAudited(user.userId) * METERS_TO_MILES
      }
      Future.successful(Ok(views.html.userProfile(s"Project Sidewalk", Some(user), auditedDistance)))
    }
  }

  /**
   * Get the list of streets that have been audited by the signed in user.
   */
  def getAuditedStreets = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val streets = AuditTaskTable.getAuditedStreets(user.userId)
        val features: List[JsObject] = streets.map { edge =>
          val coordinates: Array[Coordinate] = edge.geom.getCoordinates
          val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList
          val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)
          val properties = Json.obj(
            "street_edge_id" -> edge.streetEdgeId,
            "way_type" -> edge.wayType
          )
          Json.obj("type" -> "Feature", "geometry" -> linestring, "properties" -> properties)
        }
        val featureCollection = Json.obj("type" -> "FeatureCollection", "features" -> features)
        Future.successful(Ok(featureCollection))
      case None => Future.successful(Ok(Json.obj(
        "error" -> "0",
        "message" -> "We could not find your username in our system :("
      )))
    }
  }

  /**
   * Get the list of all streets and whether they have been audited or not, regardless of user.
   */
  def getAllStreets(filterLowQuality: Boolean) = UserAwareAction.async { implicit request =>
    val streets: List[StreetEdgeWithAuditStatus] = AuditTaskTable.selectStreetsWithAuditStatus(filterLowQuality)
    val features: List[JsObject] = streets.map { edge =>
      val coordinates: Array[Coordinate] = edge.geom.getCoordinates
      val latlngs: List[geojson.LatLng] = coordinates.map(coord => geojson.LatLng(coord.y, coord.x)).toList  // Map it to an immutable list
      val linestring: geojson.LineString[geojson.LatLng] = geojson.LineString(latlngs)
      val properties = Json.obj(
        "street_edge_id" -> edge.streetEdgeId,
        "way_type" -> edge.wayType,
        "region_id" -> edge.regionId,
        "audited" -> edge.audited
      )
      Json.obj("type" -> "Feature", "geometry" -> linestring, "properties" -> properties)
    }
    val featureCollection = Json.obj("type" -> "FeatureCollection", "features" -> features)
    Future.successful(Ok(featureCollection))
  }

  /**
   * Get the list of labels submitted by the signed in user. Only include labels in the given region if supplied.
   */
  def getSubmittedLabels(regionId: Option[Int]) = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val labels: List[LabelLocation] = LabelTable.getLabelLocations(user.userId, regionId)
        val features: List[JsObject] = labels.map { label =>
          val point = geojson.Point(geojson.LatLng(label.lat.toDouble, label.lng.toDouble))
          val properties = Json.obj(
            "audit_task_id" -> label.auditTaskId,
            "label_id" -> label.labelId,
            "gsv_panorama_id" -> label.gsvPanoramaId,
            "label_type" -> label.labelType
          )
          Json.obj("type" -> "Feature", "geometry" -> point, "properties" -> properties)
        }
        val featureCollection = Json.obj("type" -> "FeatureCollection", "features" -> features)
        Future.successful(Ok(featureCollection))
      case None =>  Future.successful(Ok(Json.obj(
        "error" -> "0",
        "message" -> "Your user id could not be found."
      )))
    }
  }

  /**
   * Get a count of the number of audits that have been completed each day.
   */
  def getAllAuditCounts = UserAwareAction.async { implicit request =>
    val auditCounts = AuditTaskTable.auditCounts
    val json = Json.arr(auditCounts.map(x => Json.obj(
      "date" -> x.date, "count" -> x.count
    )))
    Future.successful(Ok(json))
  }

  /**
   * Get a count of the number of labels that have been added each day.
   */
  def getAllLabelCounts = UserAwareAction.async { implicit request =>
    val labelCounts = LabelTable.selectLabelCountsPerDay
    val json = Json.arr(labelCounts.map(x => Json.obj(
      "date" -> x.date, "count" -> x.count
    )))
    Future.successful(Ok(json))
  }

  /**
   * Get a count of the number of validations that have been completed each day.
   */
  def getAllValidationCounts = UserAwareAction.async { implicit request =>
    val validationCounts = LabelValidationTable.getValidationsByDate
    val json = Json.arr(validationCounts.map(x => Json.obj(
      "date" -> x.date, "count" -> x.count
    )))
    Future.successful(Ok(json))
  }

  /**
   * Get up `n` recent mistakes for each label type, using validations provided by other users.
   * @param n Number of mistakes to retrieve for each label type.
   * @return
   */
  def getRecentMistakes(n: Int) = UserAwareAction.async {implicit request =>
    val labelTypes: List[String] = List("CurbRamp", "NoCurbRamp", "Obstacle", "SurfaceProblem", "Crosswalk", "Signal")
    val validations = LabelTable.getRecentValidatedLabelsForUser(request.identity.get.userId, n, labelTypes)
    val validationJson: JsValue = Json.toJson(labelTypes.map { t =>
      t -> validations.filter(_.labelType == t).map(labelMetadataUserDashToJson)
    }.toMap)
    Future.successful(Ok(validationJson))
  }

  /**
   * Sets the org of the given user. 
   *
   * @param orgId The id of the org the user is to be added to.
   *              If the id is not a valid org (e.g. 0), then the user is removed from their current org without
   *              being added to a new one.
   */
  def setUserOrg(orgId: Int) = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val userId: UUID = user.userId
        if (user.role.getOrElse("") != "Anonymous") {
          val allUserOrgs: List[Int] = UserOrgTable.getAllOrgs(userId);
          if (allUserOrgs.headOption.isEmpty) {
            UserOrgTable.save(userId, orgId)
          } else if (allUserOrgs.head != orgId) {
            UserOrgTable.remove(userId, allUserOrgs.head)
            UserOrgTable.save(userId, orgId)
          }
        }
        Future.successful(Ok(Json.obj("user_id" -> userId, "org_id" -> orgId)))
      case None =>
        Future.successful(Ok(Json.obj("error" -> "0", "message" -> "Your user id could not be found.")))
    }
  }

  /**
   * Gets some basic stats about the logged in user that we show across the site: distance, label count, and accuracy.
   */
  def getBasicUserStats = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val userId: UUID = user.userId
        // Get distance audited by the user. Convert meters to km if using metric system, to miles if using IS.
        val auditedDistance: Float = {
          if (Messages("measurement.system") == "metric") AuditTaskTable.getDistanceAudited(userId) / 1000F
          else AuditTaskTable.getDistanceAudited(userId) * METERS_TO_MILES
        }
        Future.successful(Ok(Json.obj(
          "distance_audited" -> auditedDistance,
          "label_count" -> LabelTable.countLabels(userId),
          "accuracy" -> LabelValidationTable.getUserAccuracy(userId)
        )))
      case None =>
        Future.successful(Ok(Json.obj("error" -> "0", "message" -> "Your user id could not be found.")))
    }
  }
}
