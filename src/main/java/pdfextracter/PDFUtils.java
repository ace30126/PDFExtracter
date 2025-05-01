package pdfextracter;

// Add this import
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class PDFUtils {

    // Loads a PDF document from a file
    public static PDDocument loadPdf(File file) throws IOException {
        // Disable console logging from PDFBox if desired (can be noisy)
        // System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

        // --- CHANGE THIS LINE ---
        // return PDDocument.load(file); // Old PDFBox 2.x way
        return Loader.loadPDF(file);   // New PDFBox 3.x way
        // --- END CHANGE ---
    }

    // Renders a specific page to a BufferedImage for preview
    public static BufferedImage renderPage(PDDocument document, int pageIndex, float scale) throws IOException {
        if (document == null || pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
            return null; // Or throw an exception
        }
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        // Render at a specific DPI or scale. Higher DPI = better quality but slower/more memory.
        // Let's use scaling for simplicity. Adjust 'scale' for preview size.
        // int dpi = 72; // Example DPI
        // return pdfRenderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
         return pdfRenderer.renderImage(pageIndex, scale, ImageType.RGB); // Use scale
    }

    // Saves a single page as a new PDF document
    public static void saveSinglePage(PDDocument originalDoc, int pageIndex, File outputFile) throws IOException {
        if (originalDoc == null || pageIndex < 0 || pageIndex >= originalDoc.getNumberOfPages()) {
            throw new IllegalArgumentException("Invalid page index or document.");
        }
        try (PDDocument newDoc = new PDDocument()) {
            PDPage page = originalDoc.getPage(pageIndex);
            newDoc.addPage(page); // Add the specific page
            newDoc.save(outputFile);
        } // newDoc is automatically closed here
    }

    // Saves the selected/reordered pages into a new PDF document
    public static void saveSelectedPages(PDDocument originalDoc, List<Integer> pageIndices, File outputFile) throws IOException {
        if (originalDoc == null || pageIndices == null || pageIndices.isEmpty()) {
             throw new IllegalArgumentException("No pages selected or document invalid.");
        }
        try (PDDocument newDoc = new PDDocument()) {
            for (int pageIndex : pageIndices) {
                if (pageIndex >= 0 && pageIndex < originalDoc.getNumberOfPages()) {
                    PDPage page = originalDoc.getPage(pageIndex);
                    // Import the page into the new document context
                    newDoc.importPage(page);
                } else {
                    System.err.println("Warning: Skipping invalid page index " + pageIndex);
                }
            }
            if (newDoc.getNumberOfPages() > 0) {
                 newDoc.save(outputFile);
            } else {
                throw new IOException("No valid pages were found to save.");
            }
        } // newDoc is automatically closed here
    }

    // Closes the PDF document
    public static void closePdf(PDDocument document) {
        if (document != null) {
            try {
                document.close();
            } catch (IOException e) {
                System.err.println("Error closing PDF document: " + e.getMessage());
                e.printStackTrace(); // For debugging
            }
        }
    }
}