package pdfextracter;

import org.apache.pdfbox.pdmodel.PDDocument;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.*; // 파일 드롭에 필요
import java.awt.dnd.*; // 파일 드롭에 필요
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    // --- 미리보기 패널 모양을 위한 상수 ---
    private static final int PREVIEW_IMAGE_WIDTH = 150;
    private static final float PREVIEW_SCALE = 0.2f;
    private static final int PREVIEW_SCROLLPANE_WIDTH = PREVIEW_IMAGE_WIDTH + 80;
    private static final float MAIN_PREVIEW_SCALE = 1.0f; // 메인 패널의 고품질 미리보기를 위한 배율
    private static final int MAIN_PREVIEW_MAX_WIDTH = 500; // 메인 미리보기의 최대 너비 제약 조건
    private static final int MAIN_PREVIEW_MAX_HEIGHT = 550; // 메인 미리보기의 최대 높이 제약 조건

    private JPanel mainPanelContainer;        // 명확성을 위해 mainDropPanel에서 이름 변경
    private JScrollPane mainPreviewScrollPane; // 메인 미리보기 이미지를 위한 ScrollPane
    private JLabel mainPreviewLabel;          // 메인 미리보기 이미지를 담을 라벨
    private JLabel initialDropLabel;          // 초기 "여기에 PDF 파일 드래그" 라벨
    private Component currentMainComponent = null; // 현재 mainPanelContainer 중앙에 있는 컴포넌트 추적

    private JPanel pagePreviewContainerPanel;
    private JScrollPane pagePreviewScrollPane;
    private JButton saveButton;

    private PDDocument currentDocument = null;
    private File currentPdfFile = null;
    private List<PagePreviewComponent> pageComponents = new ArrayList<>();
    private PagePreviewComponent selectedPageComponent = null; // 메인 미리보기를 위해 선택된 페이지 추적

    private JDialog progressDialog;
    private JProgressBar progressBar;
    private SwingWorker<Void, Void> mainPreviewWorker = null; // 메인 미리보기 렌더링을 위한 워커


    public MainFrame() {
        setTitle("PDFExtracter");
        setSize(900, 650);
        setMinimumSize(new Dimension(700, 450));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 진행 중인 미리보기 워커 취소
                if (mainPreviewWorker != null && !mainPreviewWorker.isDone()) {
                    mainPreviewWorker.cancel(true);	
                }
                PDFUtils.closePdf(currentDocument);
                System.out.println("애플리케이션 종료 중, PDF 문서 닫힘.");
            }
        });

        try {
            // 클래스패스 루트에서 icon.png 로드
            URL iconURL = getClass().getResource("/icon.png");
            if (iconURL != null) {
                ImageIcon appIcon = new ImageIcon(iconURL);
                setIconImage(appIcon.getImage());
            } else {
                System.err.println("Warning: 아이콘 파일(/icon.png)을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            System.err.println("Error loading icon: " + e.getMessage());
            e.printStackTrace();
        }

        createProgressDialog();
        initComponents();
        setupLayout();
        setupFileDrop(); // 명확성을 위해 setupDragAndDrop에서 이름 변경
    }

    /**
     * 모달 진행률 대화 상자를 생성합니다.
     */
    private void createProgressDialog() {
        // ... (이전과 동일) ...
        progressDialog = new JDialog(this, "PDF 로딩 중 (Loading PDF)", true); // 모달
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 30));

        JLabel loadingLabel = new JLabel("페이지 미리보기를 생성 중입니다...(Generating page previews...)");
        loadingLabel.setBorder(new EmptyBorder(10, 0, 10, 0));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20)); // 패딩
        panel.add(loadingLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        progressDialog.getContentPane().add(panel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this); // 메인 프레임 기준으로 중앙 정렬
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE); // 사용자가 닫지 못하도록 설정
    }

    /**
     * 모든 주요 UI 컴포넌트를 초기화합니다.
     */
    private void initComponents() {
        // --- 메인 패널 (중앙) ---
        mainPanelContainer = new JPanel(new BorderLayout()); // 미리보기를 위해 BorderLayout 사용
        mainPanelContainer.setBackground(new Color(220, 220, 220));

        // 파일 드롭을 위한 초기 라벨
        initialDropLabel = new JLabel("<html><div style='text-align: center;'>이곳에 PDF 파일 드래그<br/>Drag PDF File Here</div></html>");
        initialDropLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        initialDropLabel.setForeground(new Color(0, 0, 0, 100));
        initialDropLabel.setHorizontalAlignment(SwingConstants.CENTER); // 텍스트 중앙 정렬
        initialDropLabel.setVerticalAlignment(SwingConstants.CENTER);
        // 초기 라벨을 현재 메인 컴포넌트로 설정하고 추가
        currentMainComponent = initialDropLabel;
        mainPanelContainer.add(currentMainComponent, BorderLayout.CENTER);

        // 메인 페이지 미리보기를 위한 라벨 및 ScrollPane (초기에는 보이지 않음)
        mainPreviewLabel = new JLabel();
        mainPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPreviewScrollPane = new JScrollPane(mainPreviewLabel);
        mainPreviewScrollPane.setVisible(false); // 페이지 클릭 전까지 숨김
        mainPreviewScrollPane.setBorder(BorderFactory.createEmptyBorder()); // ScrollPane 자체에는 테두리 없음
        mainPreviewScrollPane.setBackground(mainPanelContainer.getBackground()); // 배경색 맞춤
        mainPreviewLabel.setOpaque(true); // 배경색을 위해 필요
        mainPreviewLabel.setBackground(mainPanelContainer.getBackground()); // 배경색 맞춤
        // mainPanelContainer.add(mainPreviewScrollPane, BorderLayout.CENTER); // 아직 추가하지 않음


        // --- 페이지 미리보기 패널 (오른쪽) ---
        pagePreviewContainerPanel = new JPanel();
        pagePreviewContainerPanel.setLayout(new BoxLayout(pagePreviewContainerPanel, BoxLayout.Y_AXIS));
        pagePreviewContainerPanel.setBackground(Color.WHITE);
        pagePreviewContainerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        pagePreviewScrollPane = new JScrollPane(pagePreviewContainerPanel);
        pagePreviewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        pagePreviewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pagePreviewScrollPane.setPreferredSize(new Dimension(PREVIEW_SCROLLPANE_WIDTH, 0));
        pagePreviewScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // --- 저장 버튼 (하단) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        saveButton = new JButton("PDF 저장 (Save PDF)");
        saveButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveModifiedPdf());
        bottomPanel.add(saveButton);
        bottomPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // --- JFrame의 Content Pane에 컴포넌트 추가 ---
        getContentPane().setLayout(new BorderLayout(5, 5));
        getContentPane().add(mainPanelContainer, BorderLayout.CENTER); // 새 컨테이너 패널 사용
        getContentPane().add(pagePreviewScrollPane, BorderLayout.EAST);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }

     /**
      * 전체 레이아웃을 설정합니다.
      */
     private void setupLayout() {
         // 메인 레이아웃은 initComponents에서 BorderLayout으로 설정됨
     }

    /**
     * 메인 패널이 PDF 파일 드롭을 수락하도록 구성합니다.
     */
    private void setupFileDrop() {
        // mainPanelContainer를 파일 드롭 대상으로 설정
        new DropTarget(mainPanelContainer, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    // 미리보기 표시와 간섭하지 않도록 배경 변경 대신 테두리를 사용하여 피드백 제공
                    mainPanelContainer.setBorder(BorderFactory.createLineBorder(Color.GREEN, 3));
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                 if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                 } else {
                    dtde.rejectDrag();
                 }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                mainPanelContainer.setBorder(null); // 테두리 제거
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                mainPanelContainer.setBorder(null); // 드롭 시 테두리 제거
                Transferable transferable = dtde.getTransferable();

                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    try {
                        List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        File pdfToLoad = null;
                        for (File file : fileList) {
                            if (file.isFile() && file.getName().toLowerCase().endsWith(".pdf")) {
                                pdfToLoad = file;
                                break;
                            }
                        }

                        if (pdfToLoad != null) {
                            System.out.println("PDF 드롭됨: " + pdfToLoad.getAbsolutePath());
                            loadPdfFile(pdfToLoad);
                            dtde.dropComplete(true);
                        } else {
                            JOptionPane.showMessageDialog(MainFrame.this,
                                    "드롭된 파일 목록에 PDF 파일이 없습니다.\nNo PDF files found in the dropped items.",
                                    "파일 오류 (File Error)", JOptionPane.WARNING_MESSAGE);
                            dtde.dropComplete(false);
                        }

                    } catch (UnsupportedFlavorException | IOException ex) {
                        System.err.println("드롭된 파일 처리 오류: " + ex.getMessage());
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "파일을 처리하는 중 오류 발생:\nError processing file: " + ex.getMessage(),
                                "오류 (Error)", JOptionPane.ERROR_MESSAGE);
                        dtde.dropComplete(false);
                    }
                } else {
                    System.out.println("드롭 거부됨: 파일 목록이 아님.");
                    dtde.rejectDrop();
                }
            }
        }, true);
    }

    /**
     * PDF를 로드하고, SwingWorker를 사용하여 미리보기를 생성하고, 진행률을 표시합니다.
     *
     * @param pdfFile 로드할 PDF 파일.
     */
    private void loadPdfFile(File pdfFile) {
        // 1. 정리
        PDFUtils.closePdf(currentDocument);
        currentDocument = null;
        currentPdfFile = null;
        pagePreviewContainerPanel.removeAll();
        pageComponents.clear();
        selectedPageComponent = null; // 선택 해제
        // 메인 패널 초기 상태로 재설정
        updateMainPanelComponent(initialDropLabel); // 초기 라벨 표시
        saveButton.setEnabled(false);

        // 2. 진행률 표시
        progressBar.setValue(0);
        SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

        // 3. 백그라운드 로딩/렌더링을 위한 워커
        SwingWorker<PDDocument, PagePreviewComponent> worker = new SwingWorker<>() {
            private Exception error = null;

            @Override
            protected PDDocument doInBackground() throws Exception {
                PDDocument doc = null;
                try {
                    doc = PDFUtils.loadPdf(pdfFile);
                    int numPages = doc.getNumberOfPages();
                    if (numPages == 0) throw new IOException("PDF 문서에 페이지가 없습니다.");

                    for (int i = 0; i < numPages; i++) {
                         if (isCancelled()) {
                             PDFUtils.closePdf(doc);
                             return null;
                         }
                        BufferedImage img = PDFUtils.renderPage(doc, i, PREVIEW_SCALE);
                        BufferedImage scaledImg = scaleImage(img, PREVIEW_IMAGE_WIDTH, -1); // 너비만 조정
                        PagePreviewComponent previewComp = new PagePreviewComponent(scaledImg, i, i + 1);
                        setupPageComponentInteractions(previewComp); // 리스너 추가
                        publish(previewComp);
                        int progress = (int) (((i + 1.0) / numPages) * 100.0);
                        setProgress(progress);
                    }
                    return doc;
                } catch (Exception e) {
                    error = e;
                    PDFUtils.closePdf(doc);
                    throw e;
                }
            }

            @Override
            protected void process(List<PagePreviewComponent> chunks) {
                for (PagePreviewComponent comp : chunks) {
                    pagePreviewContainerPanel.add(comp);
                    pagePreviewContainerPanel.add(Box.createRigidArea(new Dimension(0, 5)));
                    pageComponents.add(comp);
                }
                pagePreviewContainerPanel.revalidate();
                pagePreviewContainerPanel.repaint();
                pagePreviewScrollPane.revalidate();
            }

            @Override
            protected void done() {
                progressDialog.setVisible(false);
                setCursor(Cursor.getDefaultCursor());
                try {
                    currentDocument = get();
                    if (currentDocument == null && error == null) {
                         resetUIOnLoadFailure("작업이 취소되었거나 PDF가 비어 있거나 유효하지 않습니다.");
                         return;
                    }
                    // --- 성공 ---
                    currentPdfFile = pdfFile;
                    saveButton.setEnabled(true);
                    // 메인 패널을 업데이트하여 초기에 "로드됨" 메시지 표시
                    updateMainPanelWithMessage("PDF 로드됨: " + pdfFile.getName());
                    SwingUtilities.invokeLater(() -> pagePreviewScrollPane.getVerticalScrollBar().setValue(0));
                } catch (Exception e) {
                    // --- 실패 ---
                    Throwable cause = (error != null) ? error : e.getCause();
                    if (cause == null) cause = e;
                    System.err.println("PDF 로딩 오류: " + cause.getMessage());
                    cause.printStackTrace();
                    resetUIOnLoadFailure("PDF 파일을 로드할 수 없습니다: " + cause.getMessage());
                }
            }
        };

        // 4. 진행률 리스너
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
            }
        });

        // 5. 워커 실행
        worker.execute();
    }

    /**
     * 로드 실패 후 UI를 재설정하는 도우미 메서드입니다.
     * @param errorMessage 표시할 오류 메시지.
     */
    private void resetUIOnLoadFailure(String errorMessage) {
         PDFUtils.closePdf(currentDocument);
         currentDocument = null;
         currentPdfFile = null;
         pagePreviewContainerPanel.removeAll();
         pageComponents.clear();
         selectedPageComponent = null;
         // 메인 패널 초기 상태로 재설정
         updateMainPanelComponent(initialDropLabel); // 초기 라벨 표시
         initialDropLabel.setText("<html><div style='text-align: center;'>이곳에 PDF 파일 드래그<br/>Drag PDF File Here</div></html>");
         saveButton.setEnabled(false);
         pagePreviewContainerPanel.revalidate();
         pagePreviewContainerPanel.repaint();
         JOptionPane.showMessageDialog(MainFrame.this,
                 "PDF 파일을 로드할 수 없습니다:\n" + errorMessage,
                 "PDF 로딩 오류 (PDF Load Error)", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 메인 중앙 패널의 컴포넌트를 안전하게 교체하는 도우미 메서드입니다.
     * @param newComponent 표시할 새 컴포넌트.
     */
    private void updateMainPanelComponent(Component newComponent) {
        if (currentMainComponent != null) {
            mainPanelContainer.remove(currentMainComponent); // 이전 컴포넌트 제거
        }
        currentMainComponent = newComponent;
        mainPanelContainer.add(currentMainComponent, BorderLayout.CENTER); // 새 컴포넌트 추가

        // ScrollPane의 가시성 관리
        if (currentMainComponent == mainPreviewScrollPane) {
            mainPreviewScrollPane.setVisible(true);
        } else {
            mainPreviewScrollPane.setVisible(false);
        }

        mainPanelContainer.revalidate();
        mainPanelContainer.repaint();
    }


    /**
     * 메인 중앙 패널에 텍스트 메시지를 표시하는 도우미 메서드입니다.
     * @param message 표시할 메시지 문자열.
     */
    private void updateMainPanelWithMessage(String message) {
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        updateMainPanelComponent(messageLabel); // 새 메시지 라벨로 교체
    }


    /**
     * 가로 세로 비율을 유지하면서 BufferedImage를 대상 너비 또는 높이로 조정합니다.
     * 자동으로 계산하려는 치수에 -1을 지정합니다.
     *
     * @param originalImage 조정할 이미지.
     * @param targetWidth   원하는 너비 또는 높이를 기준으로 조정하려면 -1.
     * @param targetHeight  원하는 높이 또는 너비를 기준으로 조정하려면 -1.
     * @return 조정된 BufferedImage 또는 입력이 null이면 null.
     */
    private BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        if (originalImage == null) return null;

        double scaleFactor;

        if (targetWidth > 0 && targetHeight > 0) {
            // 너비 및 높이 제약 조건 모두에 맞게 조정
            double scaleX = (double) targetWidth / originalImage.getWidth();
            double scaleY = (double) targetHeight / originalImage.getHeight();
            scaleFactor = Math.min(scaleX, scaleY); // 맞추기 위해 더 작은 배율 사용
        } else if (targetWidth > 0) {
            // 너비를 기준으로 조정
            if (originalImage.getWidth() <= targetWidth) return originalImage; // 확대 필요 없음
            scaleFactor = (double) targetWidth / originalImage.getWidth();
        } else if (targetHeight > 0) {
            // 높이를 기준으로 조정
             if (originalImage.getHeight() <= targetHeight) return originalImage; // 확대 필요 없음
            scaleFactor = (double) targetHeight / originalImage.getHeight();
        } else {
            // 잘못된 인수
            return originalImage;
        }

        // 치수가 최소 1인지 확인
        int finalWidth = Math.max(1, (int)(originalImage.getWidth() * scaleFactor));
        int finalHeight = Math.max(1, (int)(originalImage.getHeight() * scaleFactor));


        Image resultingImage = originalImage.getScaledInstance(finalWidth, finalHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = outputImage.createGraphics();
        // 선택 사항: 필요한 경우 더 나은 품질을 위해 렌더링 힌트 추가
        // g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(resultingImage, 0, 0, null);
        g2d.dispose();
        return outputImage;
    }

    // --- 페이지 컴포넌트 상호 작용 ---

    /**
     * 단일 PagePreviewComponent에 대한 상호 작용 리스너를 설정합니다.
     * 오른쪽 클릭 컨텍스트 메뉴와 클릭-미리보기를 위한 MouseListener를 추가합니다.
     * 제거됨: DnD 제스처 인식기.
     *
     * @param comp 구성할 PagePreviewComponent.
     */
    private void setupPageComponentInteractions(PagePreviewComponent comp) {
        // 1. 오른쪽 클릭 컨텍스트 메뉴
        JPopupMenu contextMenu = createContextMenu(comp);
        comp.setComponentPopupMenu(contextMenu);

        // 2. 미리보기를 위한 왼쪽 클릭 리스너
        comp.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 왼쪽 클릭 확인
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    selectAndPreviewPage(comp);
                }
            }
        });

        // --- DnD 코드 제거됨 ---
    }

    /**
     * 페이지 미리보기 컴포넌트 선택을 처리합니다. 선택 상태를 업데이트하고,
     * 선택된 컴포넌트를 강조 표시하고, 메인 패널 미리보기 업데이트를 트리거합니다.
     *
     * @param pageToSelect 클릭된 PagePreviewComponent.
     */
    private void selectAndPreviewPage(PagePreviewComponent pageToSelect) {
         if (pageToSelect == selectedPageComponent) {
             return; // 이미 선택됨, 아무 작업도 하지 않음
         }

         // 이전에 선택된 컴포넌트에서 강조 표시 제거
         if (selectedPageComponent != null) {
             selectedPageComponent.setBorder(BorderFactory.createCompoundBorder(
                     BorderFactory.createLineBorder(Color.GRAY, 1), // 기본 테두리
                     BorderFactory.createEmptyBorder(5, 5, 5, 5)
             ));
         }

         // 새 선택 항목 설정 및 강조 표시
         selectedPageComponent = pageToSelect;
         if (selectedPageComponent != null) {
              selectedPageComponent.setBorder(BorderFactory.createCompoundBorder(
                     BorderFactory.createLineBorder(Color.BLUE, 2), // 강조 테두리
                     BorderFactory.createEmptyBorder(5, 5, 5, 5)
             ));
             // 메인 패널에서 미리보기 업데이트 트리거
             showPagePreviewInMainPanel(selectedPageComponent);
         } else {
             // 선택이 해제되면 기본 메시지 표시
             updateMainPanelWithMessage("페이지를 선택하여 미리보세요 (Select a page to preview)");
         }
    }


    /**
     * 선택된 페이지의 더 큰 미리보기를 메인 패널에 렌더링하고 표시합니다.
     * SwingWorker를 사용하여 백그라운드에서 이미지를 렌더링합니다.
     *
     * @param pageComp 미리볼 페이지를 나타내는 PagePreviewComponent.
     */
    private void showPagePreviewInMainPanel(PagePreviewComponent pageComp) {
        if (currentDocument == null) return;

        // 이전 미리보기 렌더링 워커 취소
        if (mainPreviewWorker != null && !mainPreviewWorker.isDone()) {
            mainPreviewWorker.cancel(true);
            System.out.println("이전 메인 미리보기 렌더링 취소됨.");
        }

        int pageIndex = pageComp.getOriginalPageIndex();

        // 메인 패널에 로딩 표시기 표시
        updateMainPanelWithMessage("페이지 미리보기 로딩 중 (Loading page " + (pageIndex + 1) + " preview)...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));


        // 고품질 미리보기를 렌더링하기 위한 새 워커 생성
        mainPreviewWorker = new SwingWorker<>() {
            private BufferedImage previewImage = null;
            private Exception error = null;

            @Override
            protected Void doInBackground() throws Exception {
                try {
                    System.out.println("페이지 " + pageIndex + "의 메인 미리보기 렌더링 중");
                    // 더 나은 품질을 위해 더 큰 배율 사용
                    previewImage = PDFUtils.renderPage(currentDocument, pageIndex, MAIN_PREVIEW_SCALE);
                    System.out.println("페이지 " + pageIndex + "의 메인 미리보기 렌더링 완료");
                } catch (IOException e) {
                    error = e;
                    System.err.println("메인 미리보기 렌더링 오류: " + e.getMessage());
                } catch (Exception e) {
                    error = e; // 다른 잠재적 오류 포착
                     System.err.println("메인 미리보기 렌더링 중 예기치 않은 오류: " + e.getMessage());
                     e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                // 이 워커가 취소되었거나 새 선택이 발생했는지 확인
                 if (isCancelled() || selectedPageComponent != pageComp) {
                    System.out.println("메인 미리보기 렌더링 작업 완료, 그러나 결과 무시됨 (취소되었거나 선택 변경됨).");
                    return; // 취소되었거나 선택이 변경된 경우 업데이트하지 않음
                }

                if (previewImage != null && error == null) {
                    // 렌더링된 이미지를 최대 치수에 맞게 조정
                    BufferedImage scaledPreview = scaleImage(previewImage, MAIN_PREVIEW_MAX_WIDTH, MAIN_PREVIEW_MAX_HEIGHT);

                    // 메인 패널을 이미지로 업데이트
                    mainPreviewLabel.setIcon(new ImageIcon(scaledPreview));
                    mainPreviewLabel.setText(null); // 로딩 텍스트 제거
                    updateMainPanelComponent(mainPreviewScrollPane); // ScrollPane으로 교체

                    System.out.println("페이지 " + pageIndex + "의 메인 미리보기 표시됨");

                } else {
                    // 렌더링 오류 처리
                    String errorMsg = "미리보기를 생성할 수 없습니다 (Could not generate preview)";
                    if (error != null) {
                        errorMsg += ": " + error.getMessage();
                    }
                    updateMainPanelWithMessage(errorMsg);
                    System.err.println("페이지 " + pageIndex + "의 메인 미리보기 표시 실패");
                }
            }
        };

        mainPreviewWorker.execute(); // 렌더링 시작
    }


    // --- 페이지 컨테이너에 대한 DnD 설정 제거됨 ---


    /**
     * 이제 "이동"을 포함하는 오른쪽 클릭 컨텍스트 메뉴를 만듭니다.
     */
    private JPopupMenu createContextMenu(PagePreviewComponent pageComp) {
        JPopupMenu menu = new JPopupMenu();

        // --- 다운로드 액션 ---
        JMenuItem downloadItem = new JMenuItem("다운로드 (Download Page)");
        downloadItem.addActionListener(e -> downloadSinglePage(pageComp));
        menu.add(downloadItem);

        menu.addSeparator();

        // --- 이동 액션 ---
        JMenuItem moveItem = new JMenuItem("이동 (Move)");
        moveItem.addActionListener(e -> movePageAction(pageComp));
        menu.add(moveItem);

        // --- 제거/복원 액션 ---
        JMenuItem removeItem = new JMenuItem();
        removeItem.setText(pageComp.isMarkedForRemoval() ? "복원 (Restore Page)" : "제거 (Mark for Removal)");
        removeItem.addActionListener(e -> toggleRemovePage(pageComp, removeItem));
        menu.add(removeItem);

        return menu;
    }

    // --- 액션 메서드 ---

    /**
     * "이동" 액션을 처리합니다. 사용자에게 새 페이지 번호를 묻고 순서를 변경합니다.
     */
    private void movePageAction(PagePreviewComponent pageComp) {
        int currentPageNumber = -1;
        // *표시되는* (제거되지 않은) 컴포넌트 목록에서 현재 1 기반 페이지 번호 찾기
        int visibleIndex = 0;
        for(Component c : pagePreviewContainerPanel.getComponents()) {
            if (c instanceof PagePreviewComponent) {
                // 제거 표시된 페이지는 건너뜁니다.
                if (!((PagePreviewComponent) c).isMarkedForRemoval()) {
                    visibleIndex++;
                    if (c == pageComp) {
                        currentPageNumber = visibleIndex;
                        // break; // 전체 개수를 세어야 하므로 여기서 중단하지 않습니다.
                    }
                } else if (c == pageComp) {
                    // 제거 표시된 페이지는 이동할 수 없습니다.
                     JOptionPane.showMessageDialog(this,
                        "제거 표시된 페이지는 이동할 수 없습니다.\nCannot move a page marked for removal.",
                        "이동 불가 (Cannot Move)", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }
        if (currentPageNumber == -1) return; // 제거 표시된 페이지였거나 오류

        int totalVisiblePages = visibleIndex; // visibleIndex는 마지막으로 센 번호가 총 개수가 됩니다.


        String input = JOptionPane.showInputDialog(
                this,
                String.format("페이지 %d 를(을) 몇 페이지로 이동하시겠습니까? (1-%d)\nEnter new page number for page %d (1-%d):",
                        currentPageNumber, totalVisiblePages, currentPageNumber, totalVisiblePages),
                "페이지 이동 (Move Page)",
                JOptionPane.PLAIN_MESSAGE
        );

        if (input == null || input.trim().isEmpty()) {
            return; // 사용자가 취소했거나 아무것도 입력하지 않음
        }

        try {
            int targetPageNumber = Integer.parseInt(input.trim()); // 1 기반 대상

            if (targetPageNumber < 1 || targetPageNumber > totalVisiblePages) {
                throw new NumberFormatException("페이지 번호가 범위를 벗어났습니다.");
            }
            if (targetPageNumber == currentPageNumber) {
                return; // 동일한 위치로 이동
            }

            // --- 재정렬 수행 ---
            // 현재 시각적 인덱스 및 관련 스페이서 찾기
            int currentVisualIndex = -1;
            Component currentSpacer = null;
            for (int i = 0; i < pagePreviewContainerPanel.getComponentCount(); i++) {
                if (pagePreviewContainerPanel.getComponent(i) == pageComp) {
                    currentVisualIndex = i;
                    if (i + 1 < pagePreviewContainerPanel.getComponentCount() &&
                        pagePreviewContainerPanel.getComponent(i + 1) instanceof Box.Filler) {
                        currentSpacer = pagePreviewContainerPanel.getComponent(i + 1);
                    }
                    break;
                }
            }
            if (currentVisualIndex == -1) return; // 발생해서는 안 됨

            // 대상 시각적 인덱스 계산 (targetPageNumber는 1 기반)
            // N번째 보이는 컴포넌트는 시각적 인덱스 (N-1)*2에 있습니다.
            int targetVisualIndex = (targetPageNumber - 1) * 2;

            // 이전 위치에서 컴포넌트 및 스페이서 제거
            pagePreviewContainerPanel.remove(pageComp);
            if (currentSpacer != null) {
                pagePreviewContainerPanel.remove(currentSpacer);
            }

            // 새 시각적 인덱스에 컴포넌트 추가
            // 참고: 인덱스에 추가하면 후속 컴포넌트가 이동합니다.
            pagePreviewContainerPanel.add(pageComp, targetVisualIndex);
            // 컴포넌트 뒤에 스페이서 다시 추가
            if (currentSpacer != null) {
                pagePreviewContainerPanel.add(currentSpacer, targetVisualIndex + 1);
            }

            // 내부 목록 순서 업데이트 (pageComponents) - 간단하지만 덜 효율적인 방법: 다시 빌드
            pageComponents.clear();
            for(Component c : pagePreviewContainerPanel.getComponents()){
                if(c instanceof PagePreviewComponent){
                    pageComponents.add((PagePreviewComponent) c);
                }
            }

            // UI 새로 고침
            pagePreviewContainerPanel.revalidate();
            pagePreviewContainerPanel.repaint();

             // 이동된 페이지가 선택된 경우 선택 강조 표시 업데이트
             if (selectedPageComponent == pageComp) {
                 selectAndPreviewPage(pageComp); // 강조 표시 다시 적용
             }


        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "유효한 페이지 번호를 입력하세요 (1-" + totalVisiblePages + ").\nPlease enter a valid page number (1-" + totalVisiblePages + ").",
                    "잘못된 입력 (Invalid Input)", JOptionPane.ERROR_MESSAGE);
        }
    }


    /**
     * 단일 페이지 다운로드를 처리합니다. (이전과 변경 없음)
     */
    private void downloadSinglePage(PagePreviewComponent pageComp) {
        // ... (이전과 동일) ...
        if (currentDocument == null || currentPdfFile == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("단일 페이지 저장 (Save Single Page)");
        String originalFileName = currentPdfFile.getName().replaceFirst("[.][^.]+$", ""); // 확장자 제거
        String suggestedFileName = String.format("%s_page_%d.pdf", originalFileName, pageComp.getOriginalPageIndex() + 1);
        fileChooser.setSelectedFile(new File(suggestedFileName));
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".pdf");
            }
            if (fileToSave.exists()) {
                int result = JOptionPane.showConfirmDialog(this, "파일이 이미 존재합니다. 덮어쓰시겠습니까?", "덮어쓰기 확인", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.NO_OPTION) return;
            }
             setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
             try {
                PDFUtils.saveSinglePage(currentDocument, pageComp.getOriginalPageIndex(), fileToSave);
                 JOptionPane.showMessageDialog(this, "페이지가 성공적으로 저장되었습니다:\n" + fileToSave.getAbsolutePath(), "저장 완료", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException | IllegalArgumentException ex) {
                 System.err.println("단일 페이지 저장 오류: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "페이지 저장 중 오류 발생: " + ex.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
            } finally {
                 setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    /**
     * 페이지 제거 상태 전환을 처리합니다. (이전과 변경 없음)
     */
    private void toggleRemovePage(PagePreviewComponent pageComp, JMenuItem menuItem) {
        // ... (이전과 동일) ...
        boolean isCurrentlyMarked = pageComp.isMarkedForRemoval();
        pageComp.markForRemoval(!isCurrentlyMarked); // 상태 전환
        menuItem.setText(pageComp.isMarkedForRemoval() ? "복원 (Restore Page)" : "제거 (Mark for Removal)");

         // 제거된 페이지가 선택된 경우 메인 미리보기 및 선택 해제
         if (pageComp.isMarkedForRemoval() && selectedPageComponent == pageComp) {
             selectedPageComponent = null; // 선택 참조 해제
             updateMainPanelWithMessage("페이지가 제거되었습니다 (Page marked for removal)");
         }
    }

    /**
     * 수정된 PDF 저장을 처리합니다. (이전과 변경 없음)
     */
    private void saveModifiedPdf() {
        // ... (이전과 동일) ...
        if (currentDocument == null || currentPdfFile == null) {
             JOptionPane.showMessageDialog(this, "저장할 PDF가 로드되지 않았습니다.", "저장 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Integer> pagesToSaveIndices = new ArrayList<>();
        for (Component comp : pagePreviewContainerPanel.getComponents()) {
            if (comp instanceof PagePreviewComponent) {
                 PagePreviewComponent pageComp = (PagePreviewComponent) comp;
                 if (!pageComp.isMarkedForRemoval()) {
                     pagesToSaveIndices.add(pageComp.getOriginalPageIndex());
                 }
            }
        }
        if (pagesToSaveIndices.isEmpty()) {
            JOptionPane.showMessageDialog(this, "저장할 페이지가 선택되지 않았습니다.", "저장 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("수정된 PDF 저장");
        String originalFileName = currentPdfFile.getName().replaceFirst("[.][^.]+$", "");
        String suggestedFileName = String.format("%s_modified.pdf", originalFileName);
        fileChooser.setSelectedFile(new File(suggestedFileName));
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
             if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".pdf");
            }
            if (fileToSave.exists()) {
                int result = JOptionPane.showConfirmDialog(this, "파일이 이미 존재합니다. 덮어쓰시겠습니까?", "덮어쓰기 확인", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.NO_OPTION) return;
            }
             setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
             final File finalFileToSave = fileToSave;

             SwingWorker<Void, Void> saveWorker = new SwingWorker<>() {
                 private Exception error = null;
                 @Override
                 protected Void doInBackground() throws Exception {
                    try {
                        PDFUtils.saveSelectedPages(currentDocument, pagesToSaveIndices, finalFileToSave);
                    } catch (IOException | IllegalArgumentException e) {
                         error = e; throw e;
                    }
                    return null;
                 }
                 @Override
                 protected void done() {
                     setCursor(Cursor.getDefaultCursor());
                     try {
                         get();
                          JOptionPane.showMessageDialog(MainFrame.this, "PDF가 성공적으로 저장되었습니다:\n" + finalFileToSave.getAbsolutePath(), "저장 완료", JOptionPane.INFORMATION_MESSAGE);
                     } catch (Exception e) {
                         Throwable cause = (error != null) ? error : e.getCause();
                         if(cause == null) cause = e;
                         System.err.println("수정된 PDF 저장 오류: " + cause.getMessage());
                         cause.printStackTrace();
                         JOptionPane.showMessageDialog(MainFrame.this, "PDF 저장 중 오류 발생: " + cause.getMessage(), "저장 오류", JOptionPane.ERROR_MESSAGE);
                     }
                 }
             };
             saveWorker.execute();
        }
    }

} // MainFrame 클래스 끝않아야 합