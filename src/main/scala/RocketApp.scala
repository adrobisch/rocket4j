import com.googlecode.lanterna.input.Key
import com.googlecode.lanterna.terminal.Terminal
import com.googlecode.lanterna.TerminalFacade
import java.nio.charset.Charset
import javax.usb._
import javax.usb.util.UsbUtil
import scala.collection.JavaConversions._

case class Device(name: String, vendorId: String, productId: String, color: String)

class UsbRocketLauncher(usbDevice: UsbDevice) {
  val interface = usbDevice.getActiveUsbConfiguration.getUsbInterfaces.get(0).asInstanceOf[UsbInterface]
  val endpoint = interface.getUsbEndpoints.get(0).asInstanceOf[UsbEndpoint]

  val stopCommand = 5
  val shootCommand = 4

  val rightCommand = 3
  val leftCommand = 2

  val upCommand = 1 // FIXME

  val downCommand = 0

  def rotateUp = {
    executeCommand(upCommand)
  }

  def rotateDown = {
    executeCommand(downCommand)
  }

  def rotateLeft = {
    executeCommand(leftCommand)
  }

  def rotateRight = {
    executeCommand(rightCommand)
  }

  def shoot = {
    executeCommand(shootCommand, 3500)
  }

  def executeCommand(command: Int, duration: Int = 300) = {
    if (!interface.isClaimed) {
      interface.claim()
    }

    usbDevice.syncSubmit(createUsbCommand(command))
    Thread.sleep(duration)
    usbDevice.syncSubmit(createUsbCommand(stopCommand))
  }

  def createUsbCommand(command: Int) = {
    val payloadData = if (command >= 0) 1 << command else command
    val usbCommand = usbDevice.createUsbControlIrp(0x21, 0x09, 0x0200, 0)
    usbCommand.setData(Array(payloadData.toByte))
    usbCommand
  }
}

object UsbSupport {
  val supportedDevices = List(Device("circus cannon", vendorId = "0a81", productId = "0701", color = "blue"))

  def findFirstSupportedDevice: Option[UsbDevice] = {
    (for (device <- supportedDevices) yield findDevice(device)).headOption.getOrElse(None)
  }

  def findDevice(device: Device): Option[UsbDevice] = {
    UsbHostManager.getUsbServices.getRootUsbHub.getAttachedUsbDevices.map { hub =>
      findInHub(device, hub.asInstanceOf[UsbHub])
    }.find(_.isDefined).getOrElse(None)
  }

  def findInHub(searchedDevice: Device, searchHub: UsbHub): Option[UsbDevice] = {
    val childHubs = searchHub.getAttachedUsbDevices.filter(_.isInstanceOf[UsbHub])
    val devicesInChildHub: List[Option[UsbDevice]] = (for (childHub <- childHubs) yield findInHub(searchedDevice, childHub.asInstanceOf[UsbHub])).toList

    if (devicesInChildHub.isEmpty) {
      val matching = searchHub.getAttachedUsbDevices.filterNot(_.isInstanceOf[UsbHub]).collectFirst {
        case usbDevice: UsbDevice if matchesVendorAndProduct(searchedDevice, usbDevice.getUsbDeviceDescriptor) => usbDevice
      }
      matching
    } else devicesInChildHub.head
  }

  def matchesVendorAndProduct(device: Device, usbDeviceDescriptor: UsbDeviceDescriptor) = {
    val deviceVendorId = UsbUtil.toHexString(usbDeviceDescriptor.idVendor())
    val deviceProductId = UsbUtil.toHexString(usbDeviceDescriptor.idProduct())

    val matches = deviceVendorId.equalsIgnoreCase(device.vendorId) &&
    deviceProductId.equalsIgnoreCase(device.productId)

    matches
  }

  def printAllDevices = {
    printHubInfo(UsbHostManager.getUsbServices.getRootUsbHub)
  }

  def printHubInfo(usbHub: UsbHub): Unit = {
    usbHub.getAttachedUsbDevices.map { device =>
      printDeviceInfo(device.asInstanceOf[UsbDevice])
    }
  }

  def printDeviceInfo(device: UsbDevice) = {
    println(device.getClass)

    if (device.isInstanceOf[UsbHub]) {
      printHubInfo(device.asInstanceOf[UsbHub])
    } else {
      println ("\n\nDevice:")
      println("vendor id: "+ UsbUtil.toHexString(device.getUsbDeviceDescriptor.idVendor()))
      println("product id: "+ UsbUtil.toHexString(device.getUsbDeviceDescriptor.idProduct()))
      println(UsbUtil.toHexString(device.getUsbDeviceDescriptor.idProduct()))
      println(device.getUsbDeviceDescriptor.iProduct())
      println(device.getUsbDeviceDescriptor.iManufacturer())
      println()

      val configs = device.getUsbConfigurations
      configs.map { config =>
        val usbConfig = config.asInstanceOf[UsbConfiguration]
        println(usbConfig)
        usbConfig.getUsbInterfaces.map { interface =>
          val usbInterface = interface.asInstanceOf[UsbInterface]
          println(usbInterface.getUsbInterfaceDescriptor)
          usbInterface.getUsbEndpoints.map { endpoint =>
            val usbEndpoint = endpoint.asInstanceOf[UsbEndpoint]
            println(usbEndpoint.getUsbEndpointDescriptor)
          }
        }
      }
    }
  }
}

object RocketApp extends App {
  def run() = {
    UsbSupport.printAllDevices

    val device = UsbSupport.findFirstSupportedDevice

    if (device.isDefined) {
      createTerminalAndHandleInput(new UsbRocketLauncher(device.get))
    } else println("No supported device found! Following devices are supported: "+ UsbSupport.supportedDevices)
  }

  def createTerminalAndHandleInput(rocketLauncher: UsbRocketLauncher) = {
    val terminal: Terminal = TerminalFacade.createTerminal(System.in, System.out, Charset.forName("UTF8"))
    terminal.enterPrivateMode()

    var running = true

    while(running) {
      val key = terminal.readInput()
      Thread.sleep(100)
      if (key != null)
        handleInput(key)
    }

    def handleInput(key: Key) =  {
      key.getKind match {
        case Key.Kind.Escape => terminal.exitPrivateMode(); running = false
        case Key.Kind.ArrowLeft => rocketLauncher.rotateLeft
        case Key.Kind.ArrowRight => rocketLauncher.rotateRight
        case Key.Kind.ArrowUp => rocketLauncher.rotateUp
        case Key.Kind.ArrowDown => rocketLauncher.rotateDown
        case Key.Kind.Enter => rocketLauncher.shoot
        case _ =>
      }
    }
  }

  run()
}
