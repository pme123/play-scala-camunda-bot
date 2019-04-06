package pme123

package object bot {

  def createCallbackIdent(requestId: String, callbackId: String) =
    s"$requestId--$callbackId"

  def extractCallbackIdent(callbackIdent: String): (String, String) = {
    val requestIdStr :: callbackId :: Nil = callbackIdent.split("--").toList
    (requestIdStr, callbackId)
  }

  def extractCallbackId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._2

  def extractRequestId(callbackIdent: String): String =
    extractCallbackIdent(callbackIdent)._1


}
