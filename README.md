## Publications

This repository contains the code used in the following paper: 

"Gait-based Authentication: Evaluation of EnergyConsumption on Commercial Devices"\
A. Vecchio, R. Nocerino, G. Cola\
IEEE WristSense 2022

The code is based on the algorithm described in

"Continuous authentication through gait analysis on a wrist-worn device"\
Guglielmo Cola, Alessio Vecchio, Marco Avvenuti\
Pervasive and Mobile Computing\
[https://doi.org/10.1016/j.pmcj.2021.101483]

If you use this code please cite the above articles. 

## Code

The code contains an implementation of the worlking detection and anomaly detection algorithms for Android. 
The code requires some manual changes to explore the different architectural configurations and parameters of 
operation described in the article. 

This repository contains three Android Studio modules, all contained in a single project: 
- **WatchFromAccelerometer**: the app running on the watch. It uses the watch accelerometer and can be
  configured to operate in standalone mode (Local) or in combination with the SmartphoneRemoteAuthenticator.
- **WatchFromFiles**: same as the previous one, but instead of sampling the accelerometer it retrieves the
  acceleration values from a set of files. This can be useful to compare different configurations and sampling frequencies on the 
  same input data. 
- **SmartphoneRemoteAuthenticator**: the app running on the smartphone. It is used in the Partially Remote and
  Completely Remote configurations. It executes the anomaly detection algorithm and the feature extraction when needed. 


## WatchFromAccelerometer 
This module implements the watch app. Data is collected using the watch accelerometer. 
It can operate in standalone mode (Local configuration) or together with the SmartphoneAuthenticator app
running on the smartphone (Partially Remote and Completely Remote configurations).

1) Use the WatchFromAccelerometer module.

2) Select the sampling frequency. To this aim you have to modify the "Sampling Period" and 
   "Sampling Frequency" defined in the Constants.java source file (you have to use 
   one of the 4 possibilities indicated in the article). 
   You also have to modify rows 239, 278 of the SensorHandler.java file for subsampling (see the comments in the code).

3) Upload onto the smartwatch the training set, that is used for the model of the user. The training_set.csv file must be composed of a number of 
rows, where each row represents a feature vector. In detail each rows contains the values of the 19 features separated by ",". This step is not needed when testing a partially 
remote or a completely remote configuration, as the model will be placed on the smartphone. The csv file can be uploaded using an adb command like\
   adb push PATH/TO/training_set.csv /storage/emulated/0/Android/data/it.unipi.dii.smartwatchauthenticator/files
- For the Partially Remote configuration you also have to follow the instructions for the smartphone side provided below. 

4) Install the app on the smartwatch and start it. From the menu select Local or Partially Remote. 



The Completely Remote configuration requires the following steps: 
- Carry out the above procedure, but at step modify row 477 of the SensorHandler.java file, to set up subsampling (instead of rows 239, 278).
- Carry out the smartphone-side procedure indicated below. 
- Install the app on the smartwatch and start it. From the menu select Completely Remote. 

NOTE: the anomaly detection method uses 3 parameters for classifying the user. 
Such parameters must be placed in a file called "classification_parameters.txt". 
If the parameters are not provided, they are calculated and the file is created. 
If the file exists, the parameters are retrieved from the file. This means that, if the
training set is changed, the file must be removed, as the parmeters depend on the training set. 

## Smartphone side
Another app, for the smartphone side, is required in case the system operates according to the 
Partially Remote or Completely Remote configurations. The smartphone side is contained in the
SmartphoneRemoteAuthenticator module. Once started, the app will automatically connect with the 
other app running on the watch. The Android Wear OS app must be installed on the smartphone side. 

The frequency and sampling period must be manually changed similarly to the watch app (but operating on the
Constants.java file of the SmartphoneRemoteAuthenticator module). 

The training_set.csv file, which provides a model of the user, must be uploaded onto
"/storage/emulated/0/Download"

Also for the smartwatch side, the anomaly detection method uses 3 parameters for classifying the user.
Such parameters must be placed in a file called "classification_parameters.txt".
If the parameters are not provided, they are calculated and the file is created.
If the file exists, the parameters are retrieved from the file. This means that, if the
training set is changed, the file must be removed, as the parmeters depend on the training set.


## Reading acceleration values from files
It is possible to read acceleration values from files. It is useful to test different configurations and
parameters of operation on the same input data, to have a fair comparison. 

You have to use a slightly different version of the app for 
this purpose, which is contained in the WatchFromAccelrometer module.

Steps:
- Upload onto the smartwatch a "wrist" folder, which must contain the acceleration traces of the users collected separately. 
 This can be done using the following command
 
 adb push PATH/TO/THE/WRIST_FOLDER /storage/emulated/0/Android/data/it.unipi.dii.smartwatchauthenticator/files
  
## Final note
The code must be manually changed in several parts to carry out the studies presented in the paper. 
