# BlueDroid
*An Android app to control a [BlueSpray™](http://www.bluespray.net) sprinkler controller*

The BlueSpray™ sprinkler controller is a Wifi-enabled controller for a sprinkler system. It has no physical control panel so all configuration is done via a web UI from a laptop, mobile phone, etc.  Using the web UI on mobile is a bit cumbersome; the mobile version is feature-limited and the desktop version doesn't work well on a small screen. Also, the web UI does too much. I wanted a simpler task-oriented interface that I could access from my phone. So, I'm writing one.

The initial focus is not on sprinkler system control, but on garage door control. The BlueSpray™ controller has an unusual feature for a sprinkler control system: it can control your automatic garage door opener. It has pins for connecting a standard magnetic leaf switch for recognizing whether the door is open or closed, and pins for connecting to the garage door opener's control pins. Assuming the BlueSpray™ controller is reasonably close to the garage door opener, it's a simple matter to run a pair of wires from the controller to the opener and another pair to a switch mounted on the door.

So far, what the app does is very simple:

* Shows the current status of the door (open or closed) and when it entered that state.
* Provides a button that allows the door to be opened or closed.

The app also has a serious limitation: It only works when the phone it's running on can communicate directly to the controller. Since the controller is typically on a LAN behind a NATing router, and has a non-routable private IP address, this usually means that the controller and phone must be on the same network, unless the the controller has a public IP (directly, or via port forwarding from the router).

Oh, and the IP address in question is also hard-coded, presently. The next step in the development is to get it working through the WebSocket API provided by BlueSpray's server, which will enable the app to work from any network, after the user logs in.

Future plans include:
* More garage door control features, including being able to lock the door open (to stop the normal auto-closing provided by the BlueSpray™ controller).
* Sprinkler control features, including:
  * Manual run of one zone or program
  * Pause and resume of regularly-scheduled programs
  * Adjustment of conservation features
  * View of recent run history, including water usage (if configured).
* Publication on Google Play 


---
_BlueSpray™ is a trademark of Avidz LLC, 11900 Jollyvile Road #203051, Austin, TX 78720_. This app has no affiliation with Avidz.
