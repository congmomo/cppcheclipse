package com.googlecode.cppcheclipse.core;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.ui.PartInitException;

public interface IConsole {

	public abstract OutputStream getConsoleOutputStream(boolean isError);

	public abstract void print(String line) throws IOException;

	public abstract void println(String line) throws IOException;

	public abstract void show() throws PartInitException;

}