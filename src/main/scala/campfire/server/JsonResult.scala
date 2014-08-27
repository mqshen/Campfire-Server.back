package campfire.server

import spray.json.{ JsString, JsNumber, JsObject, JsValue }

/**
 * Created by GoldRatio on 8/2/14.
 */
object JsonResult {

  def buildSuccessResult(content: JsValue): JsValue = {
    new JsObject(Map("ret" -> JsNumber(0),
      "errMsg" -> JsString(""),
      "content" -> content))
  }

  def buildContactsResult(content: JsValue): JsValue = {
    new JsObject(Map("name" -> JsString("contacts"),
      "content" -> content))
  }
}
