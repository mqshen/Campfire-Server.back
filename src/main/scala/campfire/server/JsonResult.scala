package campfire.server

import play.api.libs.json._

/**
 * Created by GoldRatio on 8/2/14.
 */
object JsonResult {

  def buildSuccessResult(content: JsValue): JsValue = {
    Json.obj("ret" -> 0, "errMsg" -> "", "content" -> content)
  }

  def buildSyncResult(content: JsValue): JsValue = {
    Json.obj("name" -> "sync", "content" -> content)
  }
}
