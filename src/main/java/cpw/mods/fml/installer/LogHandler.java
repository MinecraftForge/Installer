package cpw.mods.fml.installer;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.swing.JOptionPane;

public class LogHandler {

	protected static Logger log;

	protected static void initLogger() {
		log = Logger.getLogger("SimpleInstaller");
		log.setLevel(Level.ALL);
		FileHandler fh;
		try {

			fh = new FileHandler("./simpleinstaller.log");
			log.addHandler(fh);
			fh.setFormatter(new SimpleFormatter());

		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static void logErrorWithDialog(String message, String dialogTitle) {
		log.severe(message);
		JOptionPane.showMessageDialog(null, message, dialogTitle, JOptionPane.ERROR_MESSAGE);
	}

}
