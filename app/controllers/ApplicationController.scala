package controllers

import java.sql.Timestamp
import java.time.Instant
import javax.inject.Inject
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import controllers.headers.ProvidesHeader
import controllers.helper.ControllerUtils
import controllers.helper.ControllerUtils.parseIntegerList
import models.user._
import models.amt.{AMTAssignment, AMTAssignmentTable}
import models.audit.AuditTaskInteractionTable
import models.daos.slick.DBTableDefinitions.UserTable
import models.label.TagTable.selectTagsByLabelType
import models.street.StreetEdgePriorityTable
import models.utils.{CityInfo, Configs}
import models.attribute.ConfigTable
import models.region.RegionTable
import play.api.Play
import play.api.Play.current
import play.api.i18n.{Lang, Messages}

import java.util.Calendar
import play.api.mvc._

import scala.concurrent.Future
import scala.util.Random

/**
 * Holds the HTTP requests for some of the basic web pages.
 *
 * @param env The Silhouette environment.
 */
class ApplicationController @Inject() (implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {

  /**
    * Logs that someone is coming to the site using a custom URL, then redirects to the specified page.
    * If no referrer is specified, then it just loads the landing page.
    */
  def index = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress
    val isMobile: Boolean = ControllerUtils.isMobile(request)
    val qString = request.queryString.map { case (k, v) => k.mkString -> v.mkString }

    val referrer: Option[String] = qString.get("referrer") match {
      case Some(r) => Some(r)
      case None    => qString.get("r")
    }

    referrer match {
      // If someone is coming to the site from a custom URL, log it, and send them to the correct location.
      case Some(ref) =>
        ref match {
          case "mturk" =>
            // The referrer is mechanical turk.
            val workerId: String = qString.get("workerId").get
            val assignmentId: String = qString.get("assignmentId").get
            val hitId: String = qString.get("hitId").get
            val minutes: Int = qString.get("minutes").get.toInt

            var cal = Calendar.getInstance
            cal.setTimeInMillis(timestamp.getTime)
            cal.add(Calendar.MINUTE, minutes)
            val asmtEndTime = new Timestamp(cal.getTime.getTime)

            var activityLogText: String = "Referrer=" + ref + "_workerId=" + workerId + "_assignmentId=" + assignmentId + "_hitId=" + hitId + "_minutes=" + minutes.toString
            request.identity match {
              case Some(user) =>
                // Have different cases when the user.username is the same as the workerId and when it isn't.
                user.username match {
                  case `workerId` =>
                    activityLogText = activityLogText + "_reattempt=true"
                    // Unless they are mid-assignment, create a new assignment.
                    val asmt: Option[AMTAssignment] = AMTAssignmentTable.getAssignment(workerId, assignmentId)
                    if (asmt.isEmpty) {
                      val confirmationCode = s"${Random.alphanumeric take 8 mkString("")}"
                      val asg: AMTAssignment = AMTAssignment(0, hitId, assignmentId, timestamp, asmtEndTime, workerId, confirmationCode, false)
                      val asgId: Option[Int] = Option(AMTAssignmentTable.save(asg))
                    }
                    WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, activityLogText, timestamp))
                    Future.successful(Redirect("/explore"))
                  case _ =>
                    Future.successful(Redirect(routes.UserController.signOut(request.uri)))
                    // Need to be able to login as a different user here, but the signout redirect isn't working.
                }
              case None =>
                // Unless they are mid-assignment, create a new assignment.
                val asmt: Option[AMTAssignment] = AMTAssignmentTable.getAssignment(workerId, assignmentId)
                if (asmt.isEmpty) {
                  val confirmationCode = s"${Random.alphanumeric take 8 mkString("")}"
                  val asg: AMTAssignment = AMTAssignment(0, hitId, assignmentId, timestamp, asmtEndTime, workerId, confirmationCode, false)
                  val asgId: Option[Int] = Option(AMTAssignmentTable.save(asg))
                }
                // Since the turker doesn't exist in the sidewalk_user table create new record with Turker role.
                val redirectTo = List("turkerSignUp", hitId, workerId, assignmentId).reduceLeft(_ +"/"+ _)
                Future.successful(Redirect(redirectTo))
            }

          case _ =>
            val redirectTo: String = qString.get("to") match {
              case Some(to) =>
                to
              case None =>
                "/"
            }

            val activityLogText: String = "Referrer=" + ref + "_SendTo=" + redirectTo
            request.identity match {
              case Some(user) =>
                WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, activityLogText, timestamp))
                Future.successful(Redirect(redirectTo))
              case None =>
                // UTF-8 codes needed to pass a URL that contains parameters: ? is %3F, & is %26
                Future.successful(Redirect("/anonSignUp?url=/%3F" + request.rawQueryString.replace("&", "%26")))
            }
        }
      case None =>
        // When there are no referrers, load the landing page but store the query parameters that were passed anyway.
        val activityLogText: String = "/?"+qString.keys.map(i => i.toString +"="+ qString(i).toString).mkString("&")
        request.identity match {
          case Some(user) =>
            if(qString.nonEmpty) {
              WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, activityLogText, timestamp))
              Future.successful(Redirect("/"))
            } else if (isMobile) {
              Future.successful(Redirect("/mobile"))
            } else {
              WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Index", timestamp))
              // Get city configs.
              val cityStr: String = Play.configuration.getString("city-id").get
              val mapathonLink: Option[String] = ConfigTable.getMapathonEventLink
              // Get names and URLs for other cities so we can link to them on landing page.
              val lang: Lang = request.cookies.get("PLAY_LANG").map(l => Lang(l.value))
                .getOrElse(Lang.preferred(request.acceptLanguages))
              val cityUrls: List[CityInfo] = Configs.getAllCityInfo(lang)
              // Get total audited distance. If using metric system, convert from miles to kilometers.
              val auditedDistance: Float =
                if (Messages("measurement.system") == "metric") StreetEdgePriorityTable.auditedStreetDistanceUsingPriority * 1.60934.toFloat
                else StreetEdgePriorityTable.auditedStreetDistanceUsingPriority
              Future.successful(Ok(views.html.index("Project Sidewalk", Some(user), mapathonLink, cityUrls, auditedDistance)))
            }
          case None =>
            if(qString.isEmpty){
              Future.successful(Redirect("/anonSignUp?url=/"))
            } else{
              // UTF-8 codes needed to pass a URL that contains parameters: ? is %3F, & is %26
              Future.successful(Redirect("/anonSignUp?url=/%3F" + request.rawQueryString.replace("&", "%26")))
            }
        }
    }
  }

  /**
   * Returns a page with the Leaderboard(s) on it.
   */
  def leaderboard = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Leaderboard", timestamp))
        Future.successful(Ok(views.html.leaderboardPage("Sidewalk - Leaderboard", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/leaderboard"))
    }
  }

  /**
   * Updates user language preference cookie, returns to current page.
   */
  def changeLanguage(url: String, newLang: String, clickLocation: Option[String]) = UserAwareAction.async { implicit request =>

    // Build logger string.
    val user: String = request.identity.map(_.userId.toString).getOrElse(UserTable.find("anonymous").get.userId)
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress
    val oldLang: String = request2lang.code
    val clickLoc: String = clickLocation.getOrElse("Unknown")
    val logText: String = s"Click_module=ChangeLanguage_from=${oldLang}_to=${newLang}_location=${clickLoc}_route=${url}"

    // Log the interaction. Moved the logging here from navbar.scala.html b/c the redirect was happening too fast.
    WebpageActivityTable.save(WebpageActivity(0, user, ipAddress, logText, timestamp))

    // Update the cookie and redirect.
    Future.successful(Redirect(url).withCookies(Cookie("PLAY_LANG", newLang)))
  }

  /**
    * Returns the API page.
    */
  def api = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Developer", timestamp))
        Future.successful(Ok(views.html.api("Sidewalk - API", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/api"))
    }
  }

  /**
    * Returns a help  page.
    */
  def help = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Help", timestamp))
        Future.successful(Ok(views.html.help("Sidewalk - Help", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/help"))
    }
  }

  /**
    * Returns labeling guide page.
    */
  def labelingGuide = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide", timestamp))
        Future.successful(Ok(views.html.labelingGuide("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide"))
    }
  }

  def labelingGuideCurbRamps = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide_Curb_Ramps", timestamp))
        Future.successful(Ok(views.html.labelingGuideCurbRamps("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide/curbRamps"))
    }
  }

  def labelingGuideSurfaceProblems = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide_Surface_Problems", timestamp))
        Future.successful(Ok(views.html.labelingGuideSurfaceProblems("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide/surfaceProblems"))
    }
  }

  def labelingGuideObstacles = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide_Obstacles", timestamp))
        Future.successful(Ok(views.html.labelingGuideObstacles("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide/obstacles"))
    }
  }

  def labelingGuideNoSidewalk = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide_No_Sidewalk", timestamp))
        Future.successful(Ok(views.html.labelingGuideNoSidewalk("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide/noSidewalk"))
    }
  }

  def labelingGuideOcclusion = UserAwareAction.async { implicit request =>
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    request.identity match {
      case Some(user) =>
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Labeling_Guide_Occlusion", timestamp))
        Future.successful(Ok(views.html.labelingGuideOcclusion("Sidewalk - Labeling Guide", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/labelingGuide/occlusion"))
    }
  }

  /**
    * Returns the terms page.
    */
  def terms = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Terms", timestamp))
        Future.successful(Ok(views.html.terms("Sidewalk - Terms", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/terms"))
    }
  }

  /**
    * Returns the maintenance page.
    */
  def maintenance = UserAwareAction.async { implicit request =>
    val user: String = request.identity.map(_.userId.toString).getOrElse(UserTable.find("anonymous").get.userId)
    val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
    val ipAddress: String = request.remoteAddress

    WebpageActivityTable.save(WebpageActivity(0, user, ipAddress, "Visit_Maintenance", timestamp))
    Future.successful(Ok(views.html.maintenance("Sidewalk - Maintenance")))
  }

  /**
   * Returns the results page that contains a cool visualization.
   */
  def results = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Results", timestamp))
        Future.successful(Ok(views.html.results("Sidewalk - Results", Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/results"))
    }
  }

  /**
   * Returns the LabelMap page that contains a cool visualization.
   */
  def labelMap(regions: Option[String], routes: Option[String]) = UserAwareAction.async { implicit request =>
    val regionIds: List[Int] = regions.map(parseIntegerList).getOrElse(List())
    val routeIds: List[Int] = routes.map(parseIntegerList).getOrElse(List())
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        val activityStr: String = if (regions.isEmpty) "Visit_LabelMap" else s"Visit_LabelMap_Regions=$regions"
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, activityStr, timestamp))
        Future.successful(Ok(views.html.labelMap("Sidewalk - LabelMap", Some(user), regionIds, routeIds)))
      case None =>
        // UTF-8 codes needed to pass a URL that contains parameters: ? is %3F, & is %26
        val queryParams: String = (regionIds, routeIds) match {
          case (Nil, Nil) => ""
          case (r, Nil) => s"%3Fregions=${r.mkString(",")}"
          case (Nil, r) => s"%3Froutes=${r.mkString(",")}"
          case (r, s) => s"%3Fregions=${r.mkString(",")}%26routes=${s.mkString(",")}"
        }
        Future.successful(Redirect("/anonSignUp?url=/labelmap" + queryParams))
    }
  }

  /**
   * Returns the Gallery page.
   */
  def gallery(labelType: String, neighborhoods: String, severities: String, tags: String, validationOptions: String) = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        // Get names and URLs for cities to display in Gallery dropdown.
        val lang: Lang = Configs.getLangFromRequest(request)
        val cityInfo: List[CityInfo] = Configs.getAllCityInfo(lang)
        val labelTypes: List[(String, String)] = List(
          ("Assorted", Messages("gallery.all")),
          ("CurbRamp", Messages("curb.ramp")),
          ("NoCurbRamp", Messages("missing.ramp")),
          ("Obstacle", Messages("obstacle")),
          ("SurfaceProblem", Messages("surface.problem")),
          ("Occlusion", Messages("occlusion")),
          ("NoSidewalk", Messages("no.sidewalk")),
          ("Crosswalk", Messages("crosswalk")),
          ("Signal", Messages("signal")),
          ("Other", Messages("other"))
        )
        val possibleRegions: List[Int] = RegionTable.getAllRegions.map(_.regionId)
        val (labType, possibleTags): (String, List[String]) =
          if (labelTypes.exists(x => { x._1 == labelType })) (labelType, selectTagsByLabelType(labelType).map(_.tag))
          else ("Assorted", List())

        // Make sure that list of region IDs, severities, and validation options are formatted correctly.
        val regionIdsList: List[Int] = parseIntegerList(neighborhoods).filter(possibleRegions.contains)
        val severityList: List[Int] = parseIntegerList(severities).filter(s => s > 0 && s < 6)
        val tagList: List[String] = tags.split(",").filter(possibleTags.contains).toList
        val valOptions: List[String] = validationOptions.split(",").filter(List("correct", "incorrect", "unsure", "unvalidated").contains(_)).toList

        // Log visit to Gallery.
        val activityStr: String = s"Visit_Gallery_LabelType=${labType}_RegionIDs=${regionIdsList}_Severity=${severityList}_Tags=${tagList}_Validations=$valOptions"
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, activityStr, timestamp))

        Future.successful(Ok(views.html.gallery("Sidewalk - Gallery", Some(user), cityInfo, labType, labelTypes, regionIdsList, severityList, tagList, valOptions)))
      case None =>
        // Send them through anon signup so that there activities on sidewalk gallery are logged as anon.
        // UTF-8 codes needed to pass a URL that contains parameters: ? is %3F, & is %26
        Future.successful(Redirect(s"/anonSignUp?url=/gallery%3FlabelType=$labelType%26neighborhoods=$neighborhoods%26severities=$severities%26tags=$tags%26validationOptions=$validationOptions"))
    }
  }

  /**
   * Returns a page with instructions for users who want to receive community service hours.
   */
  def serviceHoursInstructions = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_ServiceHourInstructions", timestamp))
        val isMobile: Boolean = ControllerUtils.isMobile(request)
        Future.successful(Ok(views.html.serviceHoursInstructions(Some(user), isMobile)))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/serviceHoursInstructions"))
    }
  }

  /**
   * Returns a page that simply shows how long the sign in user has spent using Project Sidewalk.
   */
  def timeCheck = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        if (user.role.getOrElse("") == "Anonymous") {
          Future.failed(new IdentityNotFoundException("Please log in before trying to access this page."))
        } else {
          val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
          val ipAddress: String = request.remoteAddress
          WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_TimeCheck", timestamp))
          val timeSpent: Float = AuditTaskInteractionTable.getHoursAuditingAndValidating(user.userId.toString)
          val isMobile: Boolean = ControllerUtils.isMobile(request)
          Future.successful(Ok(views.html.timeCheck(Some(user), isMobile, timeSpent)))
        }
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/timeCheck"))
    }
  }

  /**
   * Returns a page that allows a user to build a custom explore route.
   */
  def routeBuilder = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress
        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_RouteBuilder", timestamp))
        Future.successful(Ok(views.html.routeBuilder(Some(user))))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/routeBuilder"))
    }
  }

  /**
    * Returns the demo page that contains a cool visualization that is a work-in-progress.
    */
  def demo = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val timestamp: Timestamp = new Timestamp(Instant.now.toEpochMilli)
        val ipAddress: String = request.remoteAddress

        WebpageActivityTable.save(WebpageActivity(0, user.userId.toString, ipAddress, "Visit_Map", timestamp))
        val cityStr: String = Play.configuration.getString("city-id").get
        val cityNameShort: Option[String] = Play.configuration.getString(s"city-params.city-short-name.$cityStr")
        Future.successful(Ok(views.html.accessScoreDemo("Sidewalk - AccessScore", Some(user), cityStr, cityNameShort)))
      case None =>
        Future.successful(Redirect("/anonSignUp?url=/demo"))
    }
  }

  /**
    * Returns a page telling the turker that they already signed in with their worker id.
    */
  def turkerIdExists = Action.async { implicit request =>
    Future.successful(Ok(views.html.turkerIdExists("Project Sidewalk")))
  }
}
