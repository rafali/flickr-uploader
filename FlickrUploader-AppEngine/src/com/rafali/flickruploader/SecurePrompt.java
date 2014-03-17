package com.rafali.flickruploader;

import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class SecurePrompt extends javax.swing.JDialog {
	private static final String TMP_REMOTEAPI = "/tmp/remoteapi";
	private static final long serialVersionUID = 4731149010265015213L;

	static String[] getLoginPass(String defaultLogin) {
		LoginPass savedPass = getSavedPass();
		if (savedPass != null && savedPass.login.equals(defaultLogin)) {
			return new String[] { savedPass.login, savedPass.pass };
		}
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(4, 1));
		JLabel username = new JLabel("Username");
		JLabel passwordLabel = new JLabel("Password");
		JTextField textField = new JTextField(12);
		final JPasswordField passwordField = new JPasswordField(12);
		panel.add(username);
		panel.add(textField);
		panel.add(passwordLabel);
		panel.add(passwordField);

		textField.setText(defaultLogin);

		JOptionPane jop = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
		JDialog dialog = jop.createDialog("Password:");
		dialog.setVisible(true);
		int result = (Integer) jop.getValue();
		dialog.dispose();
		String password = null;
		String login = null;
		if (result == JOptionPane.OK_OPTION && passwordField.getPassword().length > 0) {
			login = textField.getText();
			password = new String(passwordField.getPassword());
			savePass(new LoginPass(login, password));
		}
		return new String[] { login, password };
	}

	static class LoginPass implements Serializable {
		private static final long serialVersionUID = 1L;

		public LoginPass(String login, String pass) {
			this.login = login;
			this.pass = pass;
		}

		String login;
		String pass;
	}

	public static void savePass(LoginPass ish) {
		try {
			FileOutputStream fos = new FileOutputStream(TMP_REMOTEAPI);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(ish);
			oos.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static LoginPass getSavedPass() {
		try {
			File file = new File(TMP_REMOTEAPI);
			if (file.exists()) {
				FileInputStream fin = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fin);
				LoginPass iHandler = (LoginPass) ois.readObject();
				ois.close();
				return iHandler;
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}

}