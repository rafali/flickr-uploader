package com.rafali.flickruploader.tool;

import com.google.common.base.Joiner;
import com.rafali.common.AndroidRpcInterface;
import com.rafali.common.ToolString;
import com.rafali.flickruploader.Config;
import com.rafali.flickruploader.FlickrUploader;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.androidannotations.api.BackgroundExecutor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLHandshakeException;

public final class RPC {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(RPC.class);

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int RETRY_SLEEP_TIME_MILLIS = 2000;

    public static class UserAgentInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request.Builder builder = originalRequest.newBuilder()
                    .removeHeader("User-Agent")
                    .addHeader("User-Agent", "rafali-android-rpc gzip")
                    .addHeader("rafali-versionCode", "" + Config.VERSION)
                    .addHeader("rafali-packageName", FlickrUploader.getAppContext().getPackageName())
                    .addHeader("Content-Type", "application/json;charset=UTF-8");
            Request requestWithUserAgent = builder.build();
            return chain.proceed(requestWithUserAgent);
        }
    }

    static final OkHttpClient client = new OkHttpClient();

    static {
        client.networkInterceptors().add(new UserAgentInterceptor());
    }

    private static String postRpc(Method method, Object[] args) {
        String responseStr = null;
        boolean retry = true;
        int executionCount = 0;
        while (retry) {
            try {
                if (executionCount > 0) {
                    Thread.sleep(RETRY_SLEEP_TIME_MILLIS * executionCount);
                }
                executionCount++;
                Request.Builder builder = new Request.Builder().url(Config.HTTP_START + "/androidRpc?method=" + method.getName());
                if (args != null) {
                    // String encode = ToolStream.encode(args);
                    // LOG.debug("encoded string : " + encode.length());
                    // LOG.debug("gzipped string : " + ToolStream.encodeGzip(args).length());
                    Object[] args_logs = new Object[3];
                    args_logs[0] = args;
                    builder.post(RequestBody.create(MediaType.parse("*/*"), Streams.encodeGzip(args_logs)));
                } else {
                    builder.post(RequestBody.create(MediaType.parse("*/*"), ""));
                }

                LOG.info("postRpc " + method.getName() + "(" + Joiner.on(',').useForNull("null").join(args) + ")");

                Request request = builder.build();
                Call call = client.newCall(request);
                Response response = call.execute();

                responseStr = response.body().string();
                retry = false;
            } catch (Throwable e) {
                LOG.error("Failed androidRpc (" + e.getClass().getCanonicalName() + ")  executionCount=" + executionCount + " : " + method.getName() + " : " + Arrays.toString(args));
                if (e instanceof InterruptedIOException || e instanceof SSLHandshakeException) {
                    retry = false;
                } else if (executionCount >= DEFAULT_MAX_RETRIES) {
                    retry = false;
                }
                if (e instanceof UnknownHostException || e instanceof SocketException) {
                    notifyNetworkError();
                }
            }
        }
        return responseStr;
    }

    private static long lastNotifyNetworkError = 0;

    static void notifyNetworkError() {
        if (System.currentTimeMillis() - lastNotifyNetworkError > 10000) {
            lastNotifyNetworkError = System.currentTimeMillis();
            Utils.toast("Network error, retrying…");
        }
    }

    private static final AndroidRpcInterface rpcService = (AndroidRpcInterface) Proxy.newProxyInstance(RpcHandler.class.getClassLoader(), new Class<?>[]{AndroidRpcInterface.class},
            new RpcHandler());

    public static AndroidRpcInterface getRpcService() {
        return rpcService;
    }

    private static class RpcHandler implements InvocationHandler {

        // HTTP response should stay in cache for 5 seconds
        private static final int HTTP_CACHE_TIMEOUT = 5000;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // LOG.trace("method : " + method);
            if (args == null) {
                args = new Object[0];
            }
            final String key = method.getName() + "-" + getKey(args);

            Object lock = locks.get(key);
            long start = System.currentTimeMillis();
            if (method.getReturnType() != void.class && lock != null && !responses.containsKey(key)) {
                synchronized (lock) {
                    do {
                        try {
                            LOG.info("###### concurrent RPC call " + method.getReturnType().getSimpleName() + " " + method.getName() + "(" + Arrays.toString(args) + ")");
                            lock.wait(2 * HTTP_CACHE_TIMEOUT);
                        } catch (InterruptedException e) {
                        }
                    }
                    while (!responses.containsKey(key) && System.currentTimeMillis() - start < HTTP_CACHE_TIMEOUT);
                }
            } else {
                lock = new Object();
                locks.put(key, lock);
            }
            try {
                Object object = null;
                String response;
                if (responses.containsKey(key)) {
                    LOG.info("###### response still in cache : " + key + ", after " + ToolString.formatDuration(System.currentTimeMillis() - start));
                    object = responses.get(key);
                } else {
                    int retry = 0;
                    while (retry < DEFAULT_MAX_RETRIES) {
                        retry++;
                        response = postRpc(method, args);
                        if (ToolString.isBlank(response)) {
                            break;
                        } else {
                            object = Streams.decode(response);
                            if (object instanceof Throwable) {
                                LOG.warn("retry:" + retry + ", server exception :" + object.getClass().getName() + "," + ((Throwable) object).getMessage());
                            } else {
                                break;
                            }
                        }
                    }

                    if (object != null) {
                        responses.put(key, object);
                        BackgroundExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                responses.remove(key);
                            }
                        }, HTTP_CACHE_TIMEOUT);
                    }
                }
                if (object instanceof Throwable) {
                    if (object instanceof StreamCorruptedException || object instanceof IOException) {
                        LOG.warn(object.getClass().getSimpleName() + " on " + method.getName());
                    } else {
                        LOG.error("Server error calling " + method.getName(), (Throwable) object);
                    }
                    return null;
                    // throw new Exception(throwable.getClass().getSimpleName() + " : " + throwable.getMessage());
                }
                return object;
            } finally {
                synchronized (lock) {
                    lock.notifyAll();
                    locks.remove(key);
                }
            }
        }

        private Map<String, Object> responses = new ConcurrentHashMap<>();
        private Map<String, Object> locks = new ConcurrentHashMap<>();

        private String getKey(Object[] args) {
            String key = "";
            for (Object object : args) {
                key += object != null ? object.hashCode() : "";
            }
            return key;
        }

    }
}
