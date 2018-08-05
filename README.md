# AutoFoci
Automated foci counting software especially designed for a high-throughput object detection in single cell images.. Analysed images were obtained by immunofluorescence microscopy using a double labelling approach with two well established DNA double-strand break (DSB) markers, γH2AX and 53BP1 to visualize DSBs as so-called foci after the exposure of cells to low radiation doses.
This repository contains all files needed to perform both, the image processing to generate  single cell images as well as the subsequent counting of foci with AutoFoci.

You can download the complete repository as a zip file via [this link](https://github.com/nleng/autoFoci/archive/master.zip). 

## 1. Generation of single cell images

The ImageJ version including the cellect tools for image processing and test images (both provided as zip file) needed to try  this step out, are included in the ImageJ folder. Images created by this step are ready to be analysed in AutoFoci. Please follow the instructions in the PDF guide(link).



## 2. Foci counting with AutoFoci

The program AutoFoci (provided as [autoFoci.jar](autoFoci/autoFoci.jar?raw=true) file) and test images (provided as zip file) needed to test foci counting with AutoFoci are included in the AutoFoci folder. Please follow the instructions in the PDF guide (link). To use AutoFoci, please make sure that [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) is installed on your operating system. The ImageJ version used within AutoFoci is not compatible with Java 9, but we are working on support for it. The ImageJ version used within autoFoci is not compatible with Java 9, but we are working on support for it

