package campfire.session

import campfire.database.models.User

/**
 * Created by goldratio on 8/7/14.
 */
case class Session(id: String, userName: String, login: Boolean = true) {

}
