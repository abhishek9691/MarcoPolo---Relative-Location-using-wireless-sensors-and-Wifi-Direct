# MarcoPolo---Relative-Location-using-wireless-sensors-and-Wifi-Direct

This is an Android application developed for use with Android 4.3 and above. This application collects data from the various sensors found in smartphones, but specifically focusing on the accelerometer and GPS location. The signal from the accelerometer is passed through a Discrete Fourier Transform operation (DFT) to reduce the signal noise. This accelerometer data is then used to determine something I call the "Z level", which equates to your Z coordinate level relative to the initial start position when the application just started. This can be -1, -2 or +1, +2. This all depends on whether or not you are moving up and down stairs, or remain on the same level. This can allow one to track where they are going relative to their start position in a 3-D format, no longer just 2 dimensions as normal maps do. 
In addition, WiFi direct is used to communicate 2 nodes (Android phones) together, and allow them to transfer their data so that they can also compared where each person is located relative to each other. This includes their Z level, and location. The problem here is that the Z level does not provide an accurate 3-D relative location yet, only in 2-D. This will be expanded and built upon, but this is provided simply as a proof of concept that basic sensors can be used to map a building/track a user's location in 3 dimensions. 
