package org.jim.common.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jim.common.http.HttpConst.RequestBodyFormat;
import org.jim.common.utils.HttpParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.exception.AioDecodeException;
import org.tio.core.exception.LengthOverflowException;
import org.tio.core.utils.ByteBufferUtils;

import cn.hutool.core.util.StrUtil;

/**
 *
 * @author WChao
 *
 */
public class HttpRequestDecoder {
	public static enum Step {
		firstline, header, body
	}

	private static Logger log = LoggerFactory.getLogger(HttpRequestDecoder.class);

	/**
	 * 头部，最多有多少字节
	 */
	public static final int MAX_LENGTH_OF_HEADER = 20480;

	/**
	 * 头部，每行最大的字节数
	 */
	public static final int MAX_LENGTH_OF_HEADERLINE = 2048;

	public static HttpRequest decode(ByteBuffer buffer, ChannelContext channelContext,boolean isBody) throws AioDecodeException {
		int initPosition = buffer.position();
		int readableLength = buffer.limit() - initPosition;
		//		int count = 0;
		Step step = Step.firstline;
		//		StringBuilder currLine = new StringBuilder();
		Map<String, String> headers = new HashMap<>();
		int contentLength = 0;
		byte[] bodyBytes = null;
		StringBuilder headerSb = new StringBuilder(512);
		RequestLine firstLine = null;

		while (buffer.hasRemaining()) {
			String line;
			try {
				line = ByteBufferUtils.readLine(buffer, null, MAX_LENGTH_OF_HEADERLINE);
			} catch (LengthOverflowException e) {
				throw new AioDecodeException(e);
			}

			int newPosition = buffer.position();
			if (newPosition - initPosition > MAX_LENGTH_OF_HEADER) {
				throw new AioDecodeException("max http header length " + MAX_LENGTH_OF_HEADER);
			}

			if (line == null) {
				return null;
			}

			headerSb.append(line).append("\r\n");
			if ("".equals(line) && isBody) {//头部解析完成了
				String contentLengthStr = headers.get(HttpConst.RequestHeaderKey.Content_Length);
				if (StringUtils.isBlank(contentLengthStr)) {
					contentLength = 0;
				} else {
					contentLength = Integer.parseInt(contentLengthStr);
				}

//				int readableLength = buffer.limit() - buffer.position();
				int headerLength = (buffer.position() - initPosition);
				int allNeedLength = headerLength + contentLength; //这个packet所需要的字节长度(含头部和体部)
				if (readableLength >= allNeedLength) {
					step = Step.body;
					break;
				} else {
					channelContext.setPacketNeededLength(allNeedLength);
					return null;
				}
			} else {
				if (step == Step.firstline) {
					firstLine = parseRequestLine(line, channelContext);
					step = Step.header;
				} else if (step == Step.header) {
					if("".equals(line) && !isBody)//不解析包体的话,结束(换句话说就是只解析请求行与请求头)
						break;
					KeyValue keyValue = parseHeaderLine(line);
					headers.put(keyValue.getKey(), keyValue.getValue());
				}
				continue;
			}
		}

		if (step != Step.body && isBody) {
			return null;
		}

		if (!headers.containsKey(HttpConst.RequestHeaderKey.Host)) {
			throw new AioDecodeException("there is no host header");
		}

		HttpRequest httpRequest = new HttpRequest(channelContext.getClientNode());
		httpRequest.setChannelContext(channelContext);
		httpRequest.setHttpConfig((HttpConfig) channelContext.getGroupContext().getAttribute(GroupContextKey.HTTP_SERVER_CONFIG));
		httpRequest.setHeaderString(headerSb.toString());
		httpRequest.setRequestLine(firstLine);
		httpRequest.setHeaders(headers);
		httpRequest.setContentLength(contentLength);

		parseQueryString(httpRequest, firstLine, channelContext);

		if (contentLength == 0) {
			//			if (StringUtils.isNotBlank(firstLine.getQuery())) {
			//				decodeParams(httpRequest.getParams(), firstLine.getQuery(), httpRequest.getCharset(), channelContext);
			//			}
		} else {
			bodyBytes = new byte[contentLength];
			buffer.get(bodyBytes);
			httpRequest.setBody(bodyBytes);
			//解析消息体
			parseBody(httpRequest, firstLine, bodyBytes, channelContext);
		}

		//解析User_Agent(浏览器操作系统等信息)
		//		String User_Agent = headers.get(HttpConst.RequestHeaderKey.User_Agent);
		//		if (StringUtils.isNotBlank(User_Agent)) {
		//			//			long start = System.currentTimeMillis();
		//			UserAgentAnalyzer userAgentAnalyzer = UserAgentAnalyzerFactory.getUserAgentAnalyzer();
		//			UserAgent userAgent = userAgentAnalyzer.parse(User_Agent);
		//			httpRequest.setUserAgent(userAgent);
		//		}

		//		StringBuilder logstr = new StringBuilder();
		//		logstr.append("\r\n------------------ websocket header start ------------------------\r\n");
		//		logstr.append(firstLine.getInitStr()).append("\r\n");
		//		Set<Entry<String, String>> entrySet = headers.entrySet();
		//		for (Entry<String, String> entry : entrySet) {
		//			logstr.append(StringUtils.leftPad(entry.getKey(), 30)).append(" : ").append(entry.getValue()).append("\r\n");
		//		}
		//		logstr.append("------------------ websocket header start ------------------------\r\n");
		//		log.error(logstr.toString());

		return httpRequest;

	}

	public static void decodeParams(Map<String, Object[]> params, String paramsStr, String charset, ChannelContext channelContext) {
		if (StrUtil.isBlank(paramsStr)) {
			return;
		}

		//		// 去掉Path部分
		//		int pathEndPos = paramsStr.indexOf('?');
		//		if (pathEndPos > 0) {
		//			paramsStr = StrUtil.subSuf(paramsStr, pathEndPos + 1);
		//		}
		//		Map<String, Object[]> ret = new HashMap<>();
		String[] keyvalues = StringUtils.split(paramsStr, "&");
		for (String keyvalue : keyvalues) {
			String[] keyvalueArr = StringUtils.split(keyvalue, "=");
			if (keyvalueArr.length != 2) {
				continue;
			}

			String key = keyvalueArr[0];
			String value = null;
			try {
				value = URLDecoder.decode(keyvalueArr[1], charset);
			} catch (UnsupportedEncodingException e) {
				log.error(channelContext.toString(), e);
			}

			Object[] existValue = params.get(key);
			if (existValue != null) {
				String[] newExistValue = new String[existValue.length + 1];
				System.arraycopy(existValue, 0, newExistValue, 0, existValue.length);
				newExistValue[newExistValue.length - 1] = value;
				params.put(key, newExistValue);
			} else {
				String[] newExistValue = new String[] { value };
				params.put(key, newExistValue);
			}
		}
		return;
	}

	/**
	 * @param args
	 *
	 * @author WChao
	 * 2017年2月22日 下午4:06:42
	 *
	 */
	public static void main(String[] args) {

	}

	/**
	 * 解析消息体
	 * @param httpRequest
	 * @param firstLine
	 * @param bodyBytes
	 * @param channelContext
	 * @throws AioDecodeException
	 * @author WChao
	 */
	private static void parseBody(HttpRequest httpRequest, RequestLine firstLine, byte[] bodyBytes, ChannelContext channelContext) throws AioDecodeException {
		parseBodyFormat(httpRequest, httpRequest.getHeaders());
		RequestBodyFormat bodyFormat = httpRequest.getBodyFormat();

		httpRequest.setBody(bodyBytes);

		if (bodyFormat == RequestBodyFormat.MULTIPART) {
			if (log.isInfoEnabled()) {
				String bodyString = null;
				if (bodyBytes != null && bodyBytes.length > 0) {
					if (log.isDebugEnabled()) {
						try {
							bodyString = new String(bodyBytes, httpRequest.getCharset());
							log.debug("{} multipart body string\r\n{}", channelContext, bodyString);
						} catch (UnsupportedEncodingException e) {
							log.error(channelContext.toString(), e);
						}
					}
				}
			}

			//【multipart/form-data; boundary=----WebKitFormBoundaryuwYcfA2AIgxqIxA0】
			String initboundary = HttpParseUtils.getPerprotyEqualValue(httpRequest.getHeaders(), HttpConst.RequestHeaderKey.Content_Type, "boundary");
			log.debug("{}, initboundary:{}", channelContext, initboundary);
			HttpMultiBodyDecoder.decode(httpRequest, firstLine, bodyBytes, initboundary, channelContext);
		} else {
			String bodyString = null;
			if (bodyBytes != null && bodyBytes.length > 0) {
				try {
					bodyString = new String(bodyBytes, httpRequest.getCharset());
					httpRequest.setBodyString(bodyString);
					if (log.isInfoEnabled()) {
						log.info("{} body string\r\n{}", channelContext, bodyString);
					}
				} catch (UnsupportedEncodingException e) {
					log.error(channelContext.toString(), e);
				}
			}

			if (bodyFormat == RequestBodyFormat.URLENCODED) {
				parseUrlencoded(httpRequest, firstLine, bodyBytes, bodyString, channelContext);
			}
		}
	}

	//	private static void parseText(HttpRequestPacket httpRequest, RequestLine firstLine, byte[] bodyBytes, String bodyString) {
	//		String paramStr = "";
	//		if (StringUtils.isNotBlank(firstLine.getQueryStr())) {
	//			paramStr += firstLine.getQueryStr();
	//		}
	//		if (bodyString != null) {
	//			if (paramStr != null) {
	//				paramStr += "&";
	//			}
	//			paramStr += bodyString;
	//		}
	//
	//		if (paramStr != null) {
	//			Map<String, List<String>> params = HttpUtil.decodeParams(paramStr, httpRequest.getCharset());
	//			httpRequest.setParams(params);
	//			log.error("paramStr:{}", paramStr);
	//			log.error("param:{}", Json.toJson(params));
	//		}
	//	}

	/**
	 * Content-Type : application/x-www-form-urlencoded; charset=UTF-8
	 * Content-Type : application/x-www-form-urlencoded; charset=UTF-8
	 * @param httpRequest
	 * @param headers
	 * @author WChao
	 */
	public static void parseBodyFormat(HttpRequest httpRequest, Map<String, String> headers) {
		String Content_Type = StringUtils.lowerCase(headers.get(HttpConst.RequestHeaderKey.Content_Type));
		RequestBodyFormat bodyFormat = null;
		if (StringUtils.contains(Content_Type, HttpConst.RequestHeaderValue.Content_Type.application_x_www_form_urlencoded)) {
			bodyFormat = RequestBodyFormat.URLENCODED;
		} else if (StringUtils.contains(Content_Type, HttpConst.RequestHeaderValue.Content_Type.multipart_form_data)) {
			bodyFormat = RequestBodyFormat.MULTIPART;
		} else {
			bodyFormat = RequestBodyFormat.TEXT;
		}
		httpRequest.setBodyFormat(bodyFormat);

		if (StringUtils.isNotBlank(Content_Type)) {
			String charset = HttpParseUtils.getPerprotyEqualValue(headers, HttpConst.RequestHeaderKey.Content_Type, "charset");
			if (StringUtils.isNotBlank(charset)) {
				httpRequest.setCharset(charset);
			}
		}
	}

	/**
	 * 解析请求头的每一行
	 * @param line
	 * @return
	 *
	 * @author WChao
	 * 2017年2月23日 下午1:37:58
	 *
	 */
	public static KeyValue parseHeaderLine(String line) {
		KeyValue keyValue = new KeyValue();
		int p = line.indexOf(":");
		if (p == -1) {
			keyValue.setKey(line);
			return keyValue;
		}

		String name = StringUtils.lowerCase(line.substring(0, p).trim());
		String value = line.substring(p + 1).trim();

		keyValue.setKey(name);
		keyValue.setValue(value);

		return keyValue;
	}

	/**
	 * 解析第一行(请求行)
	 * @param line
	 * @param channelContext
	 * @return
	 *
	 * @author WChao
	 * 2017年2月23日 下午1:37:51
	 *
	 */
	public static RequestLine parseRequestLine(String line, ChannelContext channelContext) throws AioDecodeException {
		try {
			int index1 = line.indexOf(' ');
			String _method = StringUtils.upperCase(line.substring(0, index1));
			Method method = Method.from(_method);
			int index2 = line.indexOf(' ', index1 + 1);
			String pathAndQuerystr = line.substring(index1 + 1, index2); // "/user/get?name=999"
			String path = null; //"/user/get"
			String queryStr = null;
			int indexOfQuestionmark = pathAndQuerystr.indexOf("?");
			if (indexOfQuestionmark != -1) {
				queryStr = StringUtils.substring(pathAndQuerystr, indexOfQuestionmark + 1);
				path = StringUtils.substring(pathAndQuerystr, 0, indexOfQuestionmark);
			} else {
				path = pathAndQuerystr;
				queryStr = "";
			}

			String protocolVersion = line.substring(index2 + 1);
			String[] pv = StringUtils.split(protocolVersion, "/");
			String protocol = pv[0];
			String version = pv[1];

			RequestLine requestLine = new RequestLine();
			requestLine.setMethod(method);
			requestLine.setPath(path);
			requestLine.setInitPath(path);
			requestLine.setPathAndQuery(pathAndQuerystr);
			requestLine.setQuery(queryStr);
			requestLine.setVersion(version);
			requestLine.setProtocol(protocol);
			requestLine.setLine(line);

			return requestLine;
		} catch (Throwable e) {
			log.error(channelContext.toString(), e);
			throw new AioDecodeException(e);
		}
	}

	/**
	 * 解析URLENCODED格式的消息体
	 * 形如： 【Content-Type : application/x-www-form-urlencoded; charset=UTF-8】
	 * @author WChao
	 */
	private static void parseUrlencoded(HttpRequest httpRequest, RequestLine firstLine, byte[] bodyBytes, String bodyString, ChannelContext channelContext) {
		//		String paramStr = "";
		//		if (StringUtils.isNotBlank(firstLine.getQuery())) {
		//			paramStr += firstLine.getQuery();
		//		}
		//		if (bodyString != null) {
		//			if (paramStr != null) {
		//				paramStr += "&";
		//			}
		//			paramStr += bodyString;
		//		}

		if (StringUtils.isNotBlank(bodyString)) {
			decodeParams(httpRequest.getParams(), bodyString, httpRequest.getCharset(), channelContext);
		}
	}

	/**
	 * 解析查询
	 * @param httpRequest
	 * @param firstLine
	 * @param channelContext
	 */
	private static void parseQueryString(HttpRequest httpRequest, RequestLine firstLine, ChannelContext channelContext) {
		String paramStr = firstLine.getQuery();
		if (StringUtils.isNotBlank(paramStr)) {
			decodeParams(httpRequest.getParams(), paramStr, httpRequest.getCharset(), channelContext);
		}
	}

	/**
	 *
	 *
	 * @author WChao
	 * 2017年2月22日 下午4:06:42
	 *
	 */
	public HttpRequestDecoder() {

	}

}
