package pme123

package object bot {

  def createCallbackIdent(requestId: Long, callbackId: String) =
    s"$requestId--$callbackId"

  def extractCallbackIdent(callbackIdent: String) = {
    val requestIdStr :: callbackId :: Nil = callbackIdent.split("--").toList
    (requestIdStr.toLong, callbackId)
  }

  def extractCallbackId(callbackIdent: String) =
    extractCallbackIdent(callbackIdent)._2

  def extractRequestId(callbackIdent: String) =
    extractCallbackIdent(callbackIdent)._1


}
