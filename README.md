
# Corvo
_Corvo_ is a tool for the visualization of dimensionally reduced single-cell transcriptomics datasets in virtual reality. Built with the scenegraphing 
and rendering library **Scenery** (https://github.com/scenerygraphics/scenery), both Corvo and its underlying infrastructure are open-source.

## Installation
Corvo is designed to be launched from CorvoLauncher (https://github.com/ljjh20/CorvoLauncher) with the accompanying data pre-processor and UI. 

For development, clone the repository and launch with a JVM-compatible IDE. 

### Clone Repository
```
git clone https://github.com/ljjh20/Corvo.git
```

### JDK Requirements
Corvo targets JDK-11. Please set the SDK and language level (Project Structure in IntelliJ) to version 11 (e.g. from AdoptOpenJDK or Temurin Eclipse).

## Usage
You may want to dedicate additional RAM, using e.g. ```-Xmx16g``` for 16GB, in the VM options.
### Datasets
A large repository of compatible single-cell transcriptomics datasets are available from the Chan Zuckerberg Initiative: https://cellxgene.cziscience.com/datasets

However, these must be pre-processed with the **PreProcess** utility in **CorvoLauncher**:

### Vosk Language Model
You will need to download a Vosk English language model from: https://alphacephei.com/vosk/models and place it in the repository root.

### Launch
Corvo is launched by running `main(arrayOf(<dataset path>, <language model path>))` in the companion object of **src/main/kotlin/graphics/scenery/corvo/XVisualization.kt**.

## Features
_Feature list to be added soon_

