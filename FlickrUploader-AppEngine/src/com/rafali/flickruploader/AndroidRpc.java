package com.rafali.flickruploader;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rafali.common.ToolString;

public class AndroidRpc extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(AndroidRpc.class.getPackage().getName());

	public static final int appMinimalSupportedVersionCode = 33;
	public static final int appCurrentVersionCode = 33;
	private static int appServerVersion = 0;

	private static final AndroidRpcImpl androidRpcImpl = new AndroidRpcImpl();
	private static final Map<String, Method> methods = new HashMap<String, Method>();
	static {
		for (Method javaMethod : AndroidRpcImpl.class.getMethods()) {
			String key = javaMethod.getName() + "_" + javaMethod.getParameterTypes().length;
			if (methods.containsKey(key)) {
				logger.error("duplicate method name : " + key);
			}
			methods.put(key, javaMethod);
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String appVersionCode = req.getHeader("rafali-versionCode");
		resp.addHeader("Content-Type", "application/xml");
		// logger.debug(req.toString());

		if (Integer.valueOf(appVersionCode) >= appMinimalSupportedVersionCode) {
			resp.addHeader("rafali-appServerVersion", "" + appServerVersion);
			resp.addHeader("rafali-appMinimalSupportedVersionCode", "" + appMinimalSupportedVersionCode);
			resp.addHeader("rafali-appCurrentVersionCode", "" + appCurrentVersionCode);
			String method = req.getParameter("method");
			String data = getData(req);
			Object[] args_logs = ToolStream.decodeGzip(data);
			Object[] args = (Object[]) args_logs[0];
			Object obj = null;
			String key = method + "_" + args.length;
			Method jMethod = methods.get(key);
			if (jMethod != null) {
				logger.debug("method : " + jMethod.getName() + ", version : " + appVersionCode + ", args : " + Arrays.toString(args));
				try {
					if (obj == null)
						obj = jMethod.invoke(androidRpcImpl, args);
				} catch (InvocationTargetException e) {
					obj = e.getTargetException();
				} catch (Throwable e) {
					obj = e;
				}
			} else {
				logger.error("unmapped method : " + method);
			}
			if (obj != null) {
				if (obj instanceof Throwable) {
					Throwable e = (Throwable) obj;
					if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().contains("Wrong userId/deviceId")) {
						// ignore
					} else {
						logger.error(e.getClass().getSimpleName() + " while " + method + "\n" + ToolString.stack2string(e));
					}
				} else {
					if (obj instanceof Collection) {
						Collection collection = (Collection) obj;
						if (collection == null || collection.isEmpty()) {
							logger.debug("androidRpc return empty list");
						} else {
							logger.debug("androidRpc return " + collection.size() + " " + collection.iterator().next().getClass().getSimpleName());
						}
					} else if (obj instanceof Object[]) {
						logger.debug("androidRpc return Object[] : " + Arrays.toString((Object[]) obj));
					} else if (obj instanceof double[]) {
						logger.debug("androidRpc return double[] : " + Arrays.toString((double[]) obj));
					} else if (obj instanceof String[]) {
						logger.debug("androidRpc return String[] : " + Arrays.toString((String[]) obj));
					} else {
						logger.debug("androidRpc return : " + obj);
					}
				}
				String base64 = ToolStream.encode(obj);
				resp.getWriter().println(base64);
			}
		}
	}

	private static String getData(HttpServletRequest req) throws IOException {
		BufferedReader reader = req.getReader();
		StringBuilder sb = new StringBuilder();
		String line = reader.readLine();
		while (line != null) {
			sb.append(line + "\n");
			line = reader.readLine();
		}
		reader.close();
		return sb.toString();
	}

}
