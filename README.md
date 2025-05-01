# PDFExtracter

A tool for visually extracting, reordering, and removing pages from PDF files.

## Overview

PDFExtracter is a desktop application designed to help you manage pages within PDF documents conveniently. It allows you to extract specific pages, change the order of pages, and remove unnecessary ones using a visual interface. This can be helpful for efficiently gathering specific information from large PDFs or preparing documents for specific needs.


**Key Features:**

* Visually preview all pages of a PDF document via thumbnails.
* Extract and save individual pages as separate PDF files.
* Mark pages for removal (exclusion from final save).
* Reorder pages to new positions within the document sequence.
* Simple drag-and-drop interface for loading PDF files.

## Installation

As this is a standalone executable (`PDFExtracter.exe`), no formal installation is required. Simply download the file and run it.

**Requirement:** This application requires **Java Runtime Environment (JRE) version 1.8.0 or higher** to be installed on your system.

## Usage

1.  **Launch the Application:** Double-click `PDFExtracter.exe`.
2.  **Load PDF:** Drag and drop your PDF file onto the designated area in the center of the application window.
3.  **Browse Previews:** Thumbnails of all pages from the PDF will appear in the right-hand panel. If there are many pages, you can scroll through this panel.
4.  **Interact with Pages (using Right-Click on Thumbnails):**
    * **View Page:** Click on a page thumbnail in the right panel. A larger preview of that page will be displayed in the central panel for detailed viewing.
    * **Download Page:** Right-click on a page thumbnail and select the "Download" menu option. This will save only that specific page as a separate PDF file.
    * **Remove Page:** Right-click on a page thumbnail and select "Remove". The page will be marked for exclusion (it won't be included when you save the modified PDF).
    * **Restore Page:** If you have marked a page for removal, you can right-click it again and select "Restore" to include it back in the document.
    * **Move Page:** Right-click on a page thumbnail, select "Move", and then enter the desired new page number. The selected page will be moved to that position in the sequence.
5.  **(Saving the Result):** *(Add instructions here on how to save the final PDF with the applied changes - e.g., "Click the 'Save Modified PDF' button...")*

## Built With

* [JAVA](https://www.java.com/) - The programming language used (Requires JRE 1.8.0+).
* [Swing](https://docs.oracle.com/javase/8/docs/api/javax/swing/package-summary.html) - The GUI toolkit for Java.
* [Apache PDFBox](https://pdfbox.apache.org/) - Java library for working with PDF documents.

## Author

* REDUCTO (https://tutoreducto.tistory.com/671)

## Creation Date

* May 1, 2025 (1 day of development)

## Version

* v1.0

## Notes

* **Distribution:** Unauthorized distribution of this program is prohibited. Please leave a comment if you are interested in using or sharing it.
* **Customization:** If you need custom features or modifications, please leave a comment to discuss.

## Acknowledgements

* This program was created with the assistance of Gemini.
