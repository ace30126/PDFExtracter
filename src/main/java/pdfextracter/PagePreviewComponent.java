package pdfextracter;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PagePreviewComponent extends JPanel {

    private final int originalPageIndex;
    private final BufferedImage previewImage;
    private final JLabel imageLabel;
    private final JLabel pageNumberLabel;
    private boolean markedForRemoval = false; // Flag for removal status

    // Constructor
    public PagePreviewComponent(BufferedImage image, int pageIndex, int displayNumber) {
        this.previewImage = image;
        this.originalPageIndex = pageIndex;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); // Vertical layout
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1), // Outer border
                BorderFactory.createEmptyBorder(5, 5, 5, 5)    // Inner padding
        ));
        setAlignmentX(Component.CENTER_ALIGNMENT); // Align center within BoxLayout parent

        if (image != null) {
            imageLabel = new JLabel(new ImageIcon(image));
            imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(imageLabel);
        } else {
             imageLabel = new JLabel("Preview N/A"); // Placeholder if image failed
             imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
             add(imageLabel);
        }

        pageNumberLabel = new JLabel("Page " + displayNumber);
        pageNumberLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(Box.createRigidArea(new Dimension(0, 5))); // Spacer
        add(pageNumberLabel);

        // Set preferred size based on image, add some padding
        if (image != null) {
            setPreferredSize(new Dimension(image.getWidth() + 20, image.getHeight() + 40));
            setMaximumSize(new Dimension(image.getWidth() + 20, image.getHeight() + 40)); // Important for BoxLayout
        } else {
             setPreferredSize(new Dimension(120, 150)); // Default size if no image
             setMaximumSize(new Dimension(120, 150));
        }

        // Tooltip shows original index for debugging/info
        setToolTipText("Original Page Index: " + originalPageIndex);
    }

    public int getOriginalPageIndex() {
        return originalPageIndex;
    }

    public boolean isMarkedForRemoval() {
        return markedForRemoval;
    }

    public void markForRemoval(boolean remove) {
        this.markedForRemoval = remove;
        // Visually indicate removal (e.g., change background, strikethrough text)
        if (remove) {
            setBackground(Color.LIGHT_GRAY); // Simple visual cue
            imageLabel.setEnabled(false); // Dim the image/text
            pageNumberLabel.setEnabled(false);
            pageNumberLabel.setText("<html><strike>Page " + (originalPageIndex + 1) + "</strike> (Removed)</html>");
        } else {
            setBackground(UIManager.getColor("Panel.background")); // Reset background
            imageLabel.setEnabled(true);
            pageNumberLabel.setEnabled(true);
            pageNumberLabel.setText("Page " + (originalPageIndex + 1));
        }
        repaint(); // Ensure visual update
    }

    // Override paintComponent for potential custom drawing later (like selection border)
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Add custom painting here if needed (e.g., a border if selected)
    }
}