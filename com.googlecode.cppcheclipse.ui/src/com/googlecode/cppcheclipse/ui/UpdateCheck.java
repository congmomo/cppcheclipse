package com.googlecode.cppcheclipse.ui;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.googlecode.cppcheclipse.core.CppcheclipsePlugin;
import com.googlecode.cppcheclipse.core.IPreferenceConstants;
import com.googlecode.cppcheclipse.core.command.UpdateCheckCommand;
import com.googlecode.cppcheclipse.core.command.Version;

public class UpdateCheck {
	private static final String DATE_PATTERN = "yyyy.MM.dd HH:mm:ss"; //$NON-NLS-1$
	private static final String DOWNLOAD_URL = "http://sourceforge.net/projects/cppcheck/"; //$NON-NLS-1$

	public static final String[][] INTERVALS = {
			{ Messages.UpdateCheck_Daily, "daily" }, //$NON-NLS-1$
			{ Messages.UpdateCheck_Weekly, "weekly" }, //$NON-NLS-1$
			{ Messages.UpdateCheck_Monthly, "monthly" } }; //$NON-NLS-1$
	private static final long[] INTERVALS_IN_MS = { 1000L * 60L * 60L * 24L,
			1000L * 60L * 60L * 24L * 7L, 1000L * 60L * 60L * 24L * 30L };

	private final boolean isSilent;

	public UpdateCheck(boolean isSilent) {
		this.isSilent = isSilent;
	}

	public static Date getLastUpdateCheckDate() {
		IPersistentPreferenceStore configuration = CppcheclipsePlugin
				.getConfigurationPreferenceStore();

		String dateString = configuration
				.getString(IPreferenceConstants.P_LAST_UPDATE_CHECK);
		if (dateString.length() == 0) {
			return null;
		}

		DateFormat format = new SimpleDateFormat(DATE_PATTERN);
		Date lastUpdateDate = null;
		try {
			lastUpdateDate = format.parse(dateString);
		} catch (ParseException e) {
			CppcheclipsePlugin.log(e);
		}
		return lastUpdateDate;
	}

	private static boolean needUpdateCheck() {
		IPersistentPreferenceStore configuration = CppcheclipsePlugin
				.getConfigurationPreferenceStore();
		if (!configuration
				.getBoolean(IPreferenceConstants.P_USE_AUTOMATIC_UPDATE_CHECK)) {
			return false;
		}

		Date lastUpdateDate = getLastUpdateCheckDate();
		if (lastUpdateDate == null) {
			return true;
		}

		Date today = new Date();

		long timeDifferenceMS = today.getTime() - lastUpdateDate.getTime();
		String updateInterval = configuration
				.getString(IPreferenceConstants.P_AUTOMATIC_UPDATE_CHECK_INTERVAL);
		for (int i = 0; i < INTERVALS.length; i++) {
			if (updateInterval.equals(INTERVALS[i][1])) {
				return timeDifferenceMS >= INTERVALS_IN_MS[i];
			}
		}
		return false;
	}

	public static boolean startUpdateCheck(boolean isSilent) {
		if (UpdateCheck.needUpdateCheck()) {
			new UpdateCheck(true).check();
			return true;
		}
		return false;
	}

	private class UpdateCheckJob extends Job {

		public UpdateCheckJob() {
			super(Messages.UpdateCheck_JobName);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			monitor.beginTask(getName(), 1);
			if (monitor.isCanceled())
				return Status.CANCEL_STATUS;

			UpdateCheckCommand updateCheck = new UpdateCheckCommand();
			Version newVersion;
			try {
				newVersion = updateCheck.run(monitor, new Console());
				DateFormat format = new SimpleDateFormat(DATE_PATTERN);
				IPersistentPreferenceStore configuration = CppcheclipsePlugin
						.getConfigurationPreferenceStore();
				configuration.setValue(
						IPreferenceConstants.P_LAST_UPDATE_CHECK, format
								.format(new Date()));
				configuration.save();
				Display display = Display.getDefault();
				display.asyncExec(new UpdateCheckNotifier(newVersion));
			} catch (Exception e) {
				if (!isSilent) {
					CppcheclipsePlugin
							.showError("Error checking for update", e); //$NON-NLS-1$
				} else {
					CppcheclipsePlugin.log(e);
				}
			}
			return Status.OK_STATUS;
		}
	}

	private class UpdateCheckNotifier implements Runnable {

		private final Version newVersion;

		public UpdateCheckNotifier(Version newVersion) {
			this.newVersion = newVersion;
		}

		public void run() {
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
					.getShell();

			if (newVersion == null) {
				if (!isSilent) {
					MessageDialog.openInformation(shell,
							Messages.UpdateCheck_NoUpdateTitle,
							Messages.UpdateCheck_NoUpdateMessage);
				}
			} else {
				boolean downloadUpdate = false;
				// only have toggle switch for update check if silent (not
				// started from preferences)
				if (isSilent) {
					MessageDialogWithToggle msgDialog = MessageDialogWithToggle
							.openYesNoQuestion(shell,
									Messages.UpdateCheck_UpdateTitle,
									Messages.bind(
											Messages.UpdateCheck_UpdateMessage,
											newVersion),
									Messages.UpdateCheck_NeverCheckAgain,
									false, null, null);
					IPersistentPreferenceStore configuration = CppcheclipsePlugin
							.getConfigurationPreferenceStore();
					configuration.setValue(
							IPreferenceConstants.P_USE_AUTOMATIC_UPDATE_CHECK,
							!msgDialog.getToggleState());
					if (msgDialog.getReturnCode() == IDialogConstants.YES_ID) {
						downloadUpdate = true;
					}
					try {
						configuration.save();
					} catch (IOException e1) {
						CppcheclipsePlugin.log(e1);
					}
				} else {
					downloadUpdate = MessageDialog.openQuestion(shell,
							Messages.UpdateCheck_UpdateTitle, Messages.bind(
									Messages.UpdateCheck_UpdateMessage,
									newVersion));
				}

				if (downloadUpdate) {
					try {
						Utils.openUrl(DOWNLOAD_URL);
					} catch (Exception e) {
						CppcheclipsePlugin.log(e);
					}
				}
			}
		}
	};

	public Job check() {
		Job job = new UpdateCheckJob();
		job.setUser(true);
		job.schedule();
		return job;
	}
}
