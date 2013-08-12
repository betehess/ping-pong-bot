package org.w3.ircbot

/** value objects */

case class Channel(name: String) {
  override def toString = name
}

case class Nick(nickname: String) {
  override def toString = nickname
}

case class Name(name: String) {
  override def toString = name
}

case class Host(hostname: String) {
  override def toString = hostname
}

case class User(nick: Nick, name: Name, host: Host) {
  override def toString = s"[$nick|$name|$host]"
}

object User {
  val r = """^([^!]*)!([^@]*)@(.*)$""".r
  def unapply(s: String): Option[User] = s match {
    case r(nickname, name, hostname) => Some(User(Nick(nickname), Name(name), Host(hostname)))
    case _ => None
  }
}
