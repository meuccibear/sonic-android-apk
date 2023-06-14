/*
 *  sonic-android-apk  Help your Android device to do more.
 *  Copyright (C) 2022 SonicCloudOrg
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.android.service

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import org.cloud.sonic.android.constants.Contants
import org.cloud.sonic.android.plugin.SonicPluginAppList
import org.cloud.sonic.android.plugin.SonicPluginWifiManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.*


class SonicManagerThread(var handler: Handler?) : Thread() {

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        Looper.prepare()
        val handler = Handler()
        val m = SonicManagerThread(handler)
        m.start()
        println("SonicPluginAllAppListService starting：start()")
        Looper.loop()
      } catch (e: InterruptedException) {
        println("ERROR:${e.message}")
      }
    }
  }

  private fun getContext(): Context {
    val activityThread = Class.forName("android.app.ActivityThread")
    val systemMain: Method = activityThread.getDeclaredMethod("systemMain")
    val objectSystemMain: Any = systemMain.invoke(null)
    val contextImpl = Class.forName("android.app.ContextImpl")
    val createSystemContext: Method =
      contextImpl.getDeclaredMethod("createSystemContext", activityThread)
    createSystemContext.isAccessible = true
    val contextInstance: Context = createSystemContext.invoke(null, objectSystemMain) as Context
    return contextInstance.createPackageContext(
      "org.cloud.sonic.android",
      Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
    )
  }

  private val REC_SERVICE_ACTION = 1
  private val SOCKET = "sonic_manager_socket"
  private lateinit var serverSocket: LocalServerSocket
  private lateinit var clientSocket: LocalSocket

  private lateinit var outputStream: OutputStream
  private lateinit var inputStream: InputStream
  var appListPlugin: SonicPluginAppList
  var wifiManager: SonicPluginWifiManager
  private var context:Context = getContext()

  init {
    appListPlugin = SonicPluginAppList(context)
    wifiManager = SonicPluginWifiManager(context)
  }

  override fun run() {

    try {
      println(String.format("creating socket %s", SOCKET))
      serverSocket = LocalServerSocket(SOCKET)
    } catch (e: IOException) {
      println(e.message)
      e.printStackTrace()
      return
    }

    manageClientConnection()

    try {
      serverSocket?.close()
    } catch (e: IOException) {
      println(e.message)
      e.printStackTrace()
    }
    println("socket closed.")
    System.exit(0)
  }

  var mHandler = object :Handler() {
    override fun handleMessage(msg: Message) {
      processOrder(msg)
    }
  }

  private fun processOrder(msg: Message){
    when (msg.what) {
      REC_SERVICE_ACTION -> {
        val recMes = msg.obj as String
        when(recMes){
          Contants.ACTION_GET_ALL_APP_INFO ->appListPlugin.getAllAppInfo(outputStream = outputStream)
          Contants.ACTION_GET_ALL_WIFI_INFO ->wifiManager.getAllWifiList(outputStream = outputStream)
        }
      }
      else -> {
        Log.e("SonicManagerThread", "why are you here?")
      }
    }
  }

  private fun manageClientConnection() {
    println(String.format("Listening on %s", SOCKET))
    val clientSocket: LocalSocket?
    try {
      clientSocket = serverSocket.accept()
      processCommand(clientSocket)
      println("client connected")
    } catch (e: IOException) {
      println("error")
    }
  }

  private fun acceptMsg() {
    while (true) {
      try {
        val buffer = ByteArray(1024)
        inputStream = clientSocket.inputStream
        val count = inputStream.read(buffer)
        val key = String(Arrays.copyOfRange(buffer, 0, count))
        println("ServerActivity mSocketOutStream==$key")
        val msg: Message = mHandler.obtainMessage(REC_SERVICE_ACTION)
        msg.obj = key
        msg.sendToTarget()
      } catch (e: IOException) {
        println("exception==" + e.fillInStackTrace().message)
        e.printStackTrace()
      }
    }
  }

  @Throws(IOException::class)
  private fun processCommand(clientSocket: LocalSocket) {
    outputStream = clientSocket.outputStream
    acceptMsg()
  }
}
