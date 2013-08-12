package org.w3.ircbot

import java.net.InetSocketAddress
import akka.actor._
import reactivemongo.api._
import reactivemongo.api.indexes._
import reactivemongo.bson._
import play.api.libs.json._

case class Player(_id: BSONObjectID, name: Option[String], nicknames: Array[String])

object Player {

//  implicit val objectIDFormat: Format[BSONObjectID] = new Format[BSONObjectID] {
//    def reads(json: JsValue): JsResult[BSONObjectID] = {
//      val oid = (json \ "$oid").as[String]
//      JsSuccess(BSONObjectID(oid))
//    }
//    def writes(oid: BSONObjectID): JsValue = Json.obj("$oid" -> oid.stringify)
//  }
//
//  implicit val playerRecordFormat: Format[Player] = Json.format[Player]

}


