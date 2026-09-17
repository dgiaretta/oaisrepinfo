package info.oais.infomodel.structure.demo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.implementation.InformationObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.interfaces.InformationObject;
import info.oais.infomodel.interfaces.RepresentationInformation;
import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;
import info.oais.infomodel.interfaces.utility.OaisIfVector;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.StructureInterpreterFactory;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.StructureNodePrinter;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.kaitai.KaitaiFormatSpecification;
import info.oais.infomodel.structure.kaitai.generated.Point2d;
import info.oais.infomodel.structure.semantic.ImageSemanticRepInfo;
import info.oais.infomodel.structure.semantic.ImageViewSpecification;
import info.oais.infomodel.structure.semantic.OaisIfImagePixelTableModel;
import info.oais.infomodel.structure.semantic.OaisIfTableModel;
import info.oais.infomodel.structure.semantic.TableModelCsv;
import info.oais.infomodel.structure.semantic.TableSemanticRepInfo;
import info.oais.infomodel.structure.semantic.TableViewSpecification;
import info.oais.infomodel.structure.semantic.TimeSeriesSemanticRepInfo;
import info.oais.infomodel.structure.semantic.TimeSeriesViewSpecification;
import info.oais.infomodel.structure.semantic.VectorSemanticRepInfo;
import info.oais.infomodel.structure.semantic.VectorViewSpecification;

/**
 * Decodes the same little binary "point" record two different ways - once
 * via a Kaitai-Struct-generated parser, once via a DFDL schema run through
 * Apache Daffodil - through the common {@link ExecutableStructureRepInfo} /
 * {@link StructureNode} API, and shows it wired into an actual oaisCore
 * {@link InformationObject} rather than used standalone. Also shows the same
 * external-XML-view / editable-JTable / CSV-save capability extended to the
 * other three semantic shapes ({@link OaisIfTimeSeries}, {@link OaisIfVector},
 * {@link OaisIfImage}) - see {@link #buildTimeSeriesTree()},
 * {@link #buildVectorTree()} and {@link #buildImageTree()} below - over
 * small, hand-built {@link StructureNode} trees rather than anything decoded
 * from the "point" bytes, to keep this addition independent of the binary
 * format work above it.
 *
 * <p>Run with {@code mvn -pl oais-structure-demo exec:java} from the project
 * root once Daffodil and the Kaitai Struct runtime are resolvable (see the
 * root README for why that could not be exercised in this project's
 * original build environment).</p>
 */
public final class DemoMain {

	/**
	 * One {@link TableSemanticRepInfo}, written purely against
	 * {@link StructureNode} - see {@code info.oais.infomodel.structure.semantic}'s
	 * package Javadoc - applied unchanged below to both the Kaitai- and the
	 * DFDL-produced tree for this same "point" record. Neither tree is
	 * repeated (this demo decodes a single record, not an array of them), so
	 * its one row is the root node itself; x/y/label become the columns.
	 * This is the actual interoperability payoff: the mapping below never
	 * mentions Kaitai or DFDL, and does not need to.
	 *
	 * <p>How to view the data as a table - row selection, column names and
	 * types, and how to read a cell's value - is described entirely by the
	 * external {@code point-table-view.xml} resource, not hard-coded here, the
	 * same way {@code point.dfdl.xsd} externalises how the bytes themselves are
	 * structured. See {@link TableViewSpecification} and
	 * {@code info.oais.infomodel.structure.semantic.TableViewSpecificationReader}.</p>
	 */
	private static final TableSemanticRepInfo POINT_AS_TABLE = new TableSemanticRepInfo(
			new TableViewSpecification(resource("/point-table-view.xml")));

	/**
	 * Views {@link #buildTimeSeriesTree()} as an {@link OaisIfTimeSeries},
	 * described externally by {@code timeseries-view.xml} - see
	 * {@code info.oais.infomodel.structure.semantic.TimeSeriesViewSpecificationReader}.
	 */
	private static final TimeSeriesSemanticRepInfo EVENTS_AS_TIME_SERIES = new TimeSeriesSemanticRepInfo(
			new TimeSeriesViewSpecification(resource("/timeseries-view.xml")));

	/**
	 * Views {@link #buildVectorTree()} as an {@link OaisIfVector}, described
	 * externally by {@code vector-view.xml} - see
	 * {@code info.oais.infomodel.structure.semantic.VectorViewSpecificationReader}.
	 */
	private static final VectorSemanticRepInfo FEATURES_AS_VECTOR = new VectorSemanticRepInfo(
			new VectorViewSpecification(resource("/vector-view.xml")));

	/**
	 * Views {@link #buildImageTree()} as an {@link OaisIfImage}, described
	 * externally by {@code image-view.xml} - see
	 * {@code info.oais.infomodel.structure.semantic.ImageViewSpecificationReader}.
	 */
	private static final ImageSemanticRepInfo PIXELS_AS_IMAGE = new ImageSemanticRepInfo(
			new ImageViewSpecification(resource("/image-view.xml")));

	private DemoMain() {
	}

	public static void main(String[] args) throws Exception {
		byte[] bytes = pointBytes(42, -7, "hi");

		System.out.println("=== Engine discovery via StructureInterpreterFactory ===");
		StructureInterpreterFactory factory = new StructureInterpreterFactory();
		System.out.println("Available engines on this classpath: " + factory.availableLanguages());
		System.out.println();

		System.out.println("=== Decoded via Kaitai Struct ===");
		StructureNode kaitaiTree = decodeAndPrint(bytes, new KaitaiFormatSpecification(Point2d.class));

		System.out.println("=== Decoded via DFDL (Apache Daffodil) ===");
		StructureNode dfdlTree = decodeAndPrint(bytes, new DfdlFormatSpecification(
				DemoMain.class.getResource("/point.dfdl.xsd").toURI()));

		System.out.println("=== The same TableSemanticRepInfo (described externally by point-table-view.xml) "
				+ "applied to both engines' output ===");
		OaisIfTable kaitaiTable = POINT_AS_TABLE.apply(kaitaiTree);
		OaisIfTable dfdlTable = POINT_AS_TABLE.apply(dfdlTree);
		printAsTable("Kaitai Struct", kaitaiTable);
		printAsTable("DFDL (Apache Daffodil)", dfdlTable);

		showAsJTables(kaitaiTable, dfdlTable);

		System.out.println("=== TimeSeries / Vector / Image semantic views (hand-built StructureNode trees, "
				+ "described externally by timeseries-view.xml / vector-view.xml / image-view.xml) ===");
		OaisIfTimeSeries timeSeries = EVENTS_AS_TIME_SERIES.apply(buildTimeSeriesTree());
		OaisIfVector vector = FEATURES_AS_VECTOR.apply(buildVectorTree());
		OaisIfImage image = PIXELS_AS_IMAGE.apply(buildImageTree());
		printAsTable("TimeSeries", timeSeries);
		printAsTable("Vector", vector);
		printAsImage("Image", image);

		showSemanticViews(timeSeries, vector, image);
	}

	/**
	 * Opens a window with both engines' tables side by side as real
	 * {@link JTable}s, via {@link OaisIfTableModel} - the Swing counterpart to
	 * {@link #printAsTable}, showing the same {@code OaisIfTable}s an
	 * interactive user could scroll, resize and compare. Keeps the JVM alive
	 * (Swing's event dispatch thread is non-daemon) until the window is
	 * closed.
	 */
	private static void showAsJTables(OaisIfTable kaitaiTable, OaisIfTable dfdlTable) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("point-table-view.xml applied to both engines' output");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setLayout(new GridLayout(2, 1, 0, 8));
			frame.add(labeledTable("Kaitai Struct", kaitaiTable));
			frame.add(labeledTable("DFDL (Apache Daffodil)", dfdlTable));
			frame.setSize(420, 260);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}

	/**
	 * Opens a second window with the TimeSeries, Vector and Image semantic
	 * views, each as a real, editable, saveable {@link JTable} exactly like
	 * {@link #showAsJTables} - {@link OaisIfTimeSeries} and
	 * {@link OaisIfVector} are themselves {@link OaisIfTable}s, so they reuse
	 * {@link #labeledTable(String, OaisIfTable)} unchanged; {@link OaisIfImage}
	 * is not table-shaped, so it goes through {@link OaisIfImagePixelTableModel}
	 * and the generic {@link #labeledTable(String, TableModel)} instead.
	 */
	private static void showSemanticViews(OaisIfTimeSeries timeSeries, OaisIfVector vector, OaisIfImage image) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame(
					"TimeSeries / Vector / Image semantic views (externally described, editable, saveable)");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setLayout(new GridLayout(3, 1, 0, 8));
			frame.add(labeledTable("TimeSeries (timeseries-view.xml)", timeSeries));
			frame.add(labeledTable("Vector (vector-view.xml)", vector));
			frame.add(labeledTable("Image (image-view.xml)", new OaisIfImagePixelTableModel(image)));
			frame.setSize(420, 420);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}

	private static JComponent labeledTable(String title, OaisIfTable table) {
		return labeledTable(title, new OaisIfTableModel(table));
	}

	/**
	 * Builds one titled, editable, CSV-saveable {@link JTable} panel over any
	 * Swing {@link TableModel} - both {@link OaisIfTableModel} (for
	 * {@link OaisIfTable}s, including {@link OaisIfTimeSeries} and
	 * {@link OaisIfVector}) and {@link OaisIfImagePixelTableModel} (for
	 * {@link OaisIfImage}) land here.
	 */
	private static JComponent labeledTable(String title, TableModel model) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder(title));

		JTable jTable = new JTable(model);
		// Without this, clicking "Save as CSV..." while a cell is still being edited
		// (typed into, but not yet confirmed with Enter/Tab) cancels that edit rather
		// than committing it to the model - a well-known JTable default behaviour, and
		// exactly what was happening before this was added: the table loses focus to
		// the button/file dialog, the in-progress edit is discarded, and the CSV (read
		// from the model, which never saw the discarded edit) shows the old value.
		jTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		panel.add(new JScrollPane(jTable), BorderLayout.CENTER);

		JButton saveButton = new JButton("Save as CSV…");
		saveButton.addActionListener(event -> saveAsCsv(panel, title, jTable));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(saveButton);
		panel.add(buttons, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Saves {@code jTable}'s model's current contents - including whatever
	 * edits the user has made, since {@link TableModelCsv} reads through the
	 * live {@link TableModel} rather than the underlying, unmodified source -
	 * to a CSV file the user picks via a {@link JFileChooser}.
	 *
	 * <p>Explicitly stops any cell edit still in progress first (belt and
	 * braces alongside {@code terminateEditOnFocusLost} in
	 * {@link #labeledTable(String, TableModel)}): a cell being typed into is
	 * not yet reflected in the model at all until editing stops, committing
	 * it, so saving without this could still miss the very last edit the
	 * user made.</p>
	 */
	private static void saveAsCsv(JComponent parent, String title, JTable jTable) {
		if (jTable.isEditing()) {
			jTable.getCellEditor().stopCellEditing();
		}
		TableModel model = jTable.getModel();

		JFileChooser chooser = new JFileChooser();
		String suggestedName = title.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "") + "-table.csv";
		chooser.setSelectedFile(new java.io.File(suggestedName));
		if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		java.io.File file = chooser.getSelectedFile();
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			TableModelCsv.write(model, writer);
			JOptionPane.showMessageDialog(parent, "Saved to " + file.getAbsolutePath());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(parent, "Could not save " + file.getAbsolutePath() + ":\n" + e.getMessage(),
					"Save failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static URI resource(String name) {
		try {
			return DemoMain.class.getResource(name).toURI();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Malformed resource URI for " + name, e);
		}
	}

	/**
	 * Renders an {@link OaisIfTable} as a small text table - just enough to
	 * show that both engines' output landed in the same shape via the one
	 * shared {@link #POINT_AS_TABLE} mapping.
	 */
	private static void printAsTable(String engineName, OaisIfTable table) {
		StringBuilder header = new StringBuilder();
		for (int c = 0; c < table.getColumnCount(); c++) {
			if (c > 0) {
				header.append(" | ");
			}
			header.append(table.getColumnName(c));
		}
		System.out.println(engineName + ": " + header);
		for (long r = 0; r < table.getRowCount(); r++) {
			StringBuilder row = new StringBuilder();
			for (int c = 0; c < table.getColumnCount(); c++) {
				if (c > 0) {
					row.append(" | ");
				}
				row.append(table.getValueAt(r, c));
			}
			System.out.println("  " + row);
		}
	}

	/**
	 * Renders an {@link OaisIfImage}'s pixel grid as a small text table - the
	 * {@link OaisIfImage} counterpart to {@link #printAsTable}, since it is
	 * not itself an {@link OaisIfTable}.
	 */
	private static void printAsImage(String label, OaisIfImage image) {
		System.out.println(label + ":");
		Object[][] pixels = image.getPixelValues();
		for (Object[] row : pixels) {
			StringBuilder line = new StringBuilder();
			for (int c = 0; c < row.length; c++) {
				if (c > 0) {
					line.append(" | ");
				}
				line.append(row[c]);
			}
			System.out.println("  " + line);
		}
	}

	/**
	 * A small, hand-built (not decoded) {@link StructureNode} tree for
	 * {@link #EVENTS_AS_TIME_SERIES}: three instantaneous events, each an
	 * {@code event} child with a {@code when} (epoch millis) and a
	 * {@code label} leaf - matching what {@code timeseries-view.xml}
	 * describes.
	 */
	private static StructureNode buildTimeSeriesTree() {
		return DefaultStructureNode.builder("series", StructureNodeKind.COMPOSITE)
				.addChild(event(1_700_000_000_000L, "start"))
				.addChild(event(1_700_000_060_000L, "checkpoint"))
				.addChild(event(1_700_000_120_000L, "end"))
				.build();
	}

	private static StructureNode event(long epochMillis, String label) {
		return DefaultStructureNode.builder("event", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("when", epochMillis))
				.addChild(DefaultStructureNode.leaf("label", label))
				.build();
	}

	/**
	 * A small, hand-built (not decoded) {@link StructureNode} tree for
	 * {@link #FEATURES_AS_VECTOR}: two {@code LINE_STRING} features, each a
	 * {@code feature} child with a {@code label} leaf and repeated
	 * {@code vertex} children (each an {@code x}/{@code y} leaf pair) -
	 * matching what {@code vector-view.xml} describes.
	 */
	private static StructureNode buildVectorTree() {
		return DefaultStructureNode.builder("features", StructureNodeKind.COMPOSITE)
				.addChild(feature("boundary-north", vertex(0, 0), vertex(10, 0), vertex(10, 5)))
				.addChild(feature("boundary-south", vertex(0, 10), vertex(10, 10)))
				.build();
	}

	private static StructureNode feature(String label, StructureNode... vertices) {
		DefaultStructureNode.Builder builder = DefaultStructureNode.builder("feature", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("label", label));
		for (StructureNode vertex : vertices) {
			builder.addChild(vertex);
		}
		return builder.build();
	}

	private static StructureNode vertex(double x, double y) {
		return DefaultStructureNode.builder("vertex", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", x))
				.addChild(DefaultStructureNode.leaf("y", y))
				.build();
	}

	/**
	 * A small, hand-built (not decoded) {@link StructureNode} tree for
	 * {@link #PIXELS_AS_IMAGE}: a 3x3 grid of {@code int} pixel values, as
	 * repeated {@code row} children each with repeated {@code pixel} leaf
	 * children - matching what {@code image-view.xml} describes.
	 */
	private static StructureNode buildImageTree() {
		int[][] pixels = { { 0, 64, 128 }, { 32, 96, 160 }, { 64, 128, 192 } };
		DefaultStructureNode.Builder image = DefaultStructureNode.builder("image", StructureNodeKind.COMPOSITE);
		for (int[] rowPixels : pixels) {
			DefaultStructureNode.Builder row = DefaultStructureNode.builder("row", StructureNodeKind.COMPOSITE);
			for (int pixel : rowPixels) {
				row.addChild(DefaultStructureNode.leaf("pixel", pixel));
			}
			image.addChild(row.build());
		}
		return image.build();
	}

	/**
	 * Wraps the bytes as a real oaisCore {@link InformationObject} - a
	 * {@link DigitalObject} plus a {@link RepresentationInformation} that
	 * happens to be executable - and applies it, exactly the way this
	 * would be used inside a larger OAIS-based system rather than calling
	 * the adapter directly.
	 */
	private static StructureNode decodeAndPrint(byte[] bytes, info.oais.infomodel.structure.FormatSpecification spec)
			throws Exception {
		DigitalObject digitalObject = new DigitalObjectRefImpl(new ByteArrayInputStream(bytes));

		ExecutableStructureRepInfo structureRepInfo = new StructureInterpreterFactory().create(spec);

		InformationObject informationObject = new InformationObjectRefImpl(digitalObject, structureRepInfo);

		RepresentationInformation repInfo = informationObject.getRepresentationInformation();
		StructureNode tree = ((ExecutableStructureRepInfo) repInfo).apply(
				(DigitalObject) informationObject.getDataObject());

		System.out.println(StructureNodePrinter.print(tree));
		return tree;
	}

	private static byte[] pointBytes(int x, int y, String label) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(x);
			out.writeInt(y);
			byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
			out.writeByte(labelBytes.length);
			out.write(labelBytes);
		}
		return bytes.toByteArray();
	}
}
