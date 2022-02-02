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

The code contains an implementation of the worlking detection and anomaly detection algorithms, but it 
requires some manual changes to explore the different architectural configurations and parameters of 
operation as described in the article. 

In particular, this repository contains the version of the application that reads the acceleration values 
from a set of files, to repeat the experiments on the same set of data.

Procedure: 

1) Import this repository as an Android Studio project.

2) Upload onto the smartwatch the wrist folder, which contains the 20 acceleration traces. This can be done using the following command\
	adb push PATH/TO/THE/WRIST_FOLDER /storage/emulated/0/Android/data/it.unipi.dii.smartwatchauthenticator/files

3) Select the sampling frequancy. To this aim you have to modify the "Sampling Period" and "Sampling Frequency" defined in the Constants.java source file (you have to use 
one of the 4 possibilities indicated in the article). 
You also have to modify rows 239, 278 of the SensorHandler.java file for subsampling (see the comments in the code).

4) Upload onto the smartwatch the training set, that is used for the model of the user. The training_set.csv file must be composed of a number of 
rows, where each row represents a feature vector. In detail each rows contains the values of the 19 features separated by ",". This step is not needed when testing a partially 
remote or a completely remote configuration, as the model will be placed on the smartphone. The csv file can be uploaded using an adb command similar to the one described at step 2.
- For the Partially Remote configuration you also have to follow the instructions for the smartphone side provided below. 

5) Install the app on the smartwatch and start it. From the menu select Local or Partially Remote. 

The Completely Remote configuration requires the following steps: 
- Carry out the above procedure, but at step modify row 477 of the SensorHandler.java file, to set up subsampling (instead of rows 239, 278). -
- Carry out the smartphone-side procedure indicated below. 
- Install the app on the smartwatch and start it. From the menu select Completely Remote. 

NOTE: the anomaly detection methid uses 3 parameters for classifying the user. 
Such parameters must be placed in a file called "classification_parameters.txt". 
If the parameters are not provided, they are calculated and the file is created. 
If the file exists, the parameters are retrieved from the file. This means that, if the
training set is changed, the file must be removed, as the parmeters depend on the training set. 
