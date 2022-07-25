
# Corvo
_Corvo_ is a Scenery-dependent module for the visualisation of dimensionally reduced datasets in VR. It is currently designed for 3D UMAP or tSNE plots, offering an additional dimension for the analysis of singlecell rna sequencing datasets.    

## Installation
Scenery is written in Kotlin, which runs on the JVM. Please use a supporting IDE such as intelliJ IDEA. 

### Clone Repository
Clone the repository into your local file system.
```
git clone https://github.com/ljjh20/Corvo.git
```

### JDK Requirements
Due to the deprecation of the Nashorn engine since Java 15, a specific version of OpenJDK is required. Download AdoptOpenJDK9-11.0.9 here:

https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=openj9

Extract the compressed folder and add it as your JDK (_File/Project Structure..._ in intelliJ IDEA). 

## Run 

### Run Configurations
Make sure to select _adopt-openj9-11 version 11.0.9_ as your JDK.

You may also want to dedicate additional RAM, using e.g. ```-Xmx8g``` for 8GB.

### Launch
The environment is launched by running **src/test/tests/graphics/scenery/corvo/XVisualization.kt**.

A file system browser will appear. Two sample datasets are provided:

Small: **GMB_cellAtlas_data.csv**

Large: **TabulaMuris3Ddata.csv**
