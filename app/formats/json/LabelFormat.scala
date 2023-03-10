package formats.json

import controllers.helper.GoogleMapsHelper
import models.gsv.GSVDataExtended
import models.label.LabelTable.{LabelMetadata, LabelMetadataUserDash, LabelValidationMetadata}
import java.sql.Timestamp
import models.label._
import play.api.libs.json._
import play.api.libs.functional.syntax._

object LabelFormat {
  implicit val labelWrites: Writes[Label] = (
    (__ \ "label_id").write[Int] and
      (__ \ "audit_task_id").write[Int] and
      (__ \ "mission_id").write[Int] and
      (__ \ "gsv_panorama_id").write[String] and
      (__ \ "label_type_id").write[Int] and
      (__ \ "photographer_heading").write[Float] and
      (__ \ "photographer_pitch").write[Float] and
      (__ \ "panorama_lat").write[Float] and
      (__ \ "panorama_lng").write[Float] and
      (__ \ "deleted").write[Boolean] and
      (__ \ "temporary_label_id").writeNullable[Int] and
      (__ \ "time_created").write[Timestamp] and
      (__ \ "tutorial").write[Boolean] and
      (__ \ "street_edge_id").write[Int] and
      (__ \ "agree_count").write[Int] and
      (__ \ "disagree_count").write[Int] and
      (__ \ "notsure_count").write[Int] and
      (__ \ "correct").writeNullable[Boolean] and
      (__ \ "severity").writeNullable[Int] and
      (__ \ "temporary").write[Boolean] and
      (__ \ "description").writeNullable[String]
    )(unlift(Label.unapply))

  implicit val labelCVMetadataWrite: Writes[LabelTable.LabelCVMetadata] = (
    (__ \ "label_id").write[Int] and
      (__ \ "gsv_panorama_id").write[String] and
      (__ \ "label_type_id").write[Int] and
      (__ \ "agree_count").write[Int] and
      (__ \ "disagree_count").write[Int] and
      (__ \ "notsure_count").write[Int] and
      (__ \ "image_width").writeNullable[Int] and
      (__ \ "image_height").writeNullable[Int] and
      (__ \ "sv_image_x").write[Int] and
      (__ \ "sv_image_y").write[Int] and
      (__ \ "canvas_width").write[Int] and
      (__ \ "canvas_height").write[Int] and
      (__ \ "canvas_x").write[Int] and
      (__ \ "canvas_y").write[Int] and
      (__ \ "zoom").write[Int] and
      (__ \ "heading").write[Float] and
      (__ \ "pitch").write[Float] and
      (__ \ "photographer_heading").write[Float] and
      (__ \ "photographer_pitch").write[Float]
  )(unlift(LabelTable.LabelCVMetadata.unapply))

  implicit val gsvDataExtendedWrite: Writes[GSVDataExtended] = (
    (__ \ "gsv_panorama_id").write[String] and
      (__ \ "image_width").writeNullable[Int] and
      (__ \ "image_height").writeNullable[Int] and
      (__ \ "panorama_lat").writeNullable[Float] and
      (__ \ "panorama_lng").writeNullable[Float] and
      (__ \ "photographer_heading").writeNullable[Float] and
      (__ \ "photographer_pitch").writeNullable[Float]
    )(unlift(GSVDataExtended.unapply))

  def validationLabelMetadataToJson(labelMetadata: LabelValidationMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "label_type" -> labelMetadata.labelType,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "image_date" -> labelMetadata.imageDate,
      "label_timestamp" -> labelMetadata.timestamp,
      "heading" -> labelMetadata.heading,
      "pitch" -> labelMetadata.pitch,
      "zoom" -> labelMetadata.zoom,
      "canvas_x" -> labelMetadata.canvasX,
      "canvas_y" -> labelMetadata.canvasY,
      "canvas_width" -> labelMetadata.canvasWidth,
      "canvas_height" -> labelMetadata.canvasHeight,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "street_edge_id" -> labelMetadata.streetEdgeId,
      "region_id" -> labelMetadata.regionId,
      "correctness" -> labelMetadata.correct,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "tags" -> labelMetadata.tags
    )
  }

  def labelMetadataWithValidationToJsonAdmin(labelMetadata: LabelMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "tutorial" -> labelMetadata.tutorial,
      "image_date" -> labelMetadata.imageDate,
      "heading" -> labelMetadata.headingPitchZoom._1,
      "pitch" -> labelMetadata.headingPitchZoom._2,
      "zoom" -> labelMetadata.headingPitchZoom._3,
      "canvas_x" -> labelMetadata.canvasXY._1,
      "canvas_y" -> labelMetadata.canvasXY._2,
      "canvas_width" -> labelMetadata.canvasWidthHeight._1,
      "canvas_height" -> labelMetadata.canvasWidthHeight._2,
      "audit_task_id" -> labelMetadata.auditTaskId,
      "street_edge_id" -> labelMetadata.streetEdgeId,
      "region_id" -> labelMetadata.regionId,
      "user_id" -> labelMetadata.userId,
      "username" -> labelMetadata.username,
      "timestamp" -> labelMetadata.timestamp,
      "label_type_key" -> labelMetadata.labelTypeKey,
      "label_type_value" -> labelMetadata.labelTypeValue,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "num_agree" -> labelMetadata.validations("agree"),
      "num_disagree" -> labelMetadata.validations("disagree"),
      "num_notsure" -> labelMetadata.validations("notsure"),
      "tags" -> labelMetadata.tags
    )
  }

  // Has the label metadata excluding username, user_id, and audit_task_id.
  def labelMetadataWithValidationToJson(labelMetadata: LabelMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "tutorial" -> labelMetadata.tutorial,
      "image_date" -> labelMetadata.imageDate,
      "heading" -> labelMetadata.headingPitchZoom._1,
      "pitch" -> labelMetadata.headingPitchZoom._2,
      "zoom" -> labelMetadata.headingPitchZoom._3,
      "canvas_x" -> labelMetadata.canvasXY._1,
      "canvas_y" -> labelMetadata.canvasXY._2,
      "canvas_width" -> labelMetadata.canvasWidthHeight._1,
      "canvas_height" -> labelMetadata.canvasWidthHeight._2,
      "street_edge_id" -> labelMetadata.streetEdgeId,
      "region_id" -> labelMetadata.regionId,
      "timestamp" -> labelMetadata.timestamp,
      "label_type_key" -> labelMetadata.labelTypeKey,
      "label_type_value" -> labelMetadata.labelTypeValue,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "num_agree" -> labelMetadata.validations("agree"),
      "num_disagree" -> labelMetadata.validations("disagree"),
      "num_notsure" -> labelMetadata.validations("notsure"),
      "tags" -> labelMetadata.tags
    )
  }

  def labelMetadataUserDashToJson(label: LabelMetadataUserDash): JsObject = {
    Json.obj(
      "label_id" -> label.labelId,
      "gsv_panorama_id" -> label.gsvPanoramaId,
      "heading" -> label.heading,
      "pitch" -> label.pitch,
      "zoom" -> label.zoom,
      "canvas_x" -> label.canvasX,
      "canvas_y" -> label.canvasY,
      "canvas_width" -> label.canvasWidth,
      "canvas_height" -> label.canvasHeight,
      "label_type" -> label.labelType,
      "time_validated" -> label.timeValidated,
      "validator_comment" -> label.validatorComment,
      "image_url" -> GoogleMapsHelper.getImageUrl(label.gsvPanoramaId, label.canvasWidth, label.canvasHeight, label.heading, label.pitch, label.zoom)
    )
  }
}
