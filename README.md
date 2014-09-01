Android Beacon Library
=======================

An Android library providing APIs to interact with beacons  

## Samsung BLE SDK Support

This version of the library has included support for the Samsung BLE SDK for Samsung devices running
4.2.x.  Any device running 4.3+ will automatically use the native Android BLE SDKs.  The Samsung
BLE SDKs are based on the BlueZ stack, not the newer Bluedroid stack, and are too slow to constantly
scan for beacons.  Tests show that only 2 BLE advertising packets per second may be scanned by the
Samsung BLE SDK before it starts to fall behind.  The recommended work-around it to configure this
library to slow down scans when using the Samsung BLE SDK.  Even when doing so, if a large number of
beacons are around, processing may back up.  If enabling Samsung BLE SDK support, be sure to test
your app thoroughly on Samsung 4.2.x devices to verify proper behavior.

Because of these limitations, Samsung support is disabled by default.  In order to enable it, you must
call:

    mBeaconManager.setSamsungSdkAllowed(true);
    if (mBeaconManager.isSamsungSdkCompatible()) {
    	// if this device only has the Samsung BLE SDK then we must slow down
     	// scans.  These settings appears to keep the Samsung SDK from getting behind when two or
     	// fewer beacons are around advertising at 10 Hz.
        mBeaconManager.setForegroundScanPeriod(1100l); // 1.1 seconds
        mBeaconManager.setForegroundBetweenScanPeriod(10000l); // 10 seconds
    }

## Changes from the 0.x library version

This library has changed significantly from the 0.x library version and is now designed to work with
open AltBeacons which fully support Android without any intellectual property restrictions.  For
more information on how to migrate projects using the 0.x APIs to the 2.x APIs, see
[API migration.](api-migrate.md)

**IMPORTANT:  By default, this library will only detect beacons meeting the new AltBeacon specification.**

If you want this library to work with proprietary or custom beacons, see the [BeaconParser](http://altbeacon.github.io/android-beacon-library/javadoc/org/altbeacon/beacon/BeaconParser.html) class.

## What does this library do?

It allows Android devices to use beacons much like iOS devices do.  An app can request to get notifications when one
or more beacons appear or disappear.  An app can also request to get a ranging update from one or more beacons
at a frequency of approximately 1Hz.

## Documentation

The [project website](http://altbeacon.github.io/android-beacon-library/) has [full documentation](http://altbeacon.github.io/android-beacon-library/documentation.html) including [Javadocs.](http://altbeacon.github.io/android-beacon-library/javadoc/)

## Binary Releases

You may [download binary releases here.](http://altbeacon.github.io/android-beacon-library/download.html) 

## How to build this Library

IMPORTANT:  This project now uses an AndroidStudio/gradle build system and the source code may no longer be imported into Eclipse as a library project.
Eclipse users may download the latest release binary as a tar.gz file, which may then be imported as an Eclipse Library Project.  See the quick start on the project website for more information.

## Build Instructions

Known working with Android Studio 0.8.6 and Gradle 1.12

Key Gradle build targets:

    ./gradlew test # run unit tests
    ./gradlew build # development build
    ./gradlew release  # release build  

## License

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

This software is available under the Apache License 2.0, allowing you to use the library in your applications.

If you want to help with the open source project, contact david@radiusnetworks.com


