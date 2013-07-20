package com.kactech.cgold.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.params.CookieSpecPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.mapper.MapperWrapper;

public class XpmPriceReader {

	public static interface PriceSource {
		public String getURL();

		public Double getPrice(String source);
	}

	public static class McxPriceSource implements PriceSource {
		XStream x;
		String currency;

		public McxPriceSource(String currency) {
			this.currency = currency;
			x = new XStream() {
				protected MapperWrapper wrapMapper(MapperWrapper next) {
					return new MapperWrapper(next) {
						@Override
						public boolean shouldSerializeMember(@SuppressWarnings("rawtypes") Class definedIn,
								String fieldName) {
							if (definedIn == Doc.class && !"lprice".equals(fieldName)) {
								return false;
							}
							return super.shouldSerializeMember(definedIn, fieldName);
						}
					};
				}
			};
			x.processAnnotations(Doc.class);
		}

		@Override
		public String getURL() {
			return "https://mcxnow.com/orders?cur=" + currency;
		}

		@Override
		public Double getPrice(String source) {
			return ((Doc) x.fromXML(source)).lprice;
		}

		@XStreamAlias("doc")
		public static class Doc {
			public Double lprice;
		}
	}

	public static class CryptsyPriceSource implements PriceSource {
		Gson g = new Gson();
		String currency;

		public CryptsyPriceSource(String currency) {
			this.currency = currency;
		}

		@Override
		public String getURL() {
			return "https://www.cryptsy.com/api.php?method=marketdata";
		}

		@Override
		public Double getPrice(String source) {
			Src src = g.fromJson(source, Src.class);
			return src.marketsReturn.markets.get(currency).lasttradeprice;
		}

		public static class Src {
			@SerializedName("return")
			public Return marketsReturn;

			public static class Return {
				public Map<String, Market> markets;

				public static class Market {
					public Double lasttradeprice;
				}
			}
		}
	}

	public static class CoinsEPriceSource implements PriceSource {
		Gson g = new Gson();
		String currency;

		public CoinsEPriceSource(String currency) {
			this.currency = currency;
		}

		@Override
		public String getURL() {
			return "http://www.coins-e.com/api/v2/markets/data/";
		}

		@Override
		public Double getPrice(String source) {
			return g.fromJson(source, MarketsData.class).markets.get(currency + "_BTC").marketstat.ltp;
		}

		public static class MarketsData {
			public LinkedHashMap<String, Market> markets;

			public static class Market {
				public String c1, c2;
				public Depth marketdepth;
				public Stat marketstat;
				public String status;

				public static class Depth {
					public List<Rec> asks;
					public List<Rec> bids;

					public static class Rec {
						public Double cq, q, r;
						public Integer n;
					}
				}

				public static class Stat {
					public Double ask, ask_q, bid, bid_q, ltp, ltq, total_ask_q, total_bid_q;
					@SerializedName("24h")
					public Part p_24h;

					public static class Part {
						public Double avg_rate, l, h, volume;
					}
				}
			}
		}
	}

	public static final String USER_AGENT = "Mozilla/5.0 (X11; U; Linux x86_64; pl-PL; rv:1.9.0.15) Gecko/2009102704 Fedora/3.0.15-1.fc10 Firefox/3.0.15";
	static final Set<Header> DEFAULT_HEADERS = new HashSet<Header>();
	static {
		DEFAULT_HEADERS.add(new BasicHeader("Accept-Language", "en,en-us;q=0.7,en;q=0.3"));
		DEFAULT_HEADERS.add(new BasicHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7"));
		DEFAULT_HEADERS.add(new BasicHeader("Keep-Alive", "300"));
	}

	static HttpParams createHttpParams() {
		HttpParams params = new BasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setUserAgent(params, USER_AGENT);
		params.setBooleanParameter(CookieSpecPNames.SINGLE_COOKIE_HEADER, true);
		params.setBooleanParameter(ClientPNames.HANDLE_AUTHENTICATION, false);
		params.setParameter(ClientPNames.HANDLE_REDIRECTS, null);
		params.setParameter(ClientPNames.DEFAULT_HEADERS, DEFAULT_HEADERS);
		params.setParameter(ClientPNames.CONNECTION_MANAGER_FACTORY_CLASS_NAME, CMF.class.getName());
		return params;
	}

	public static class CMF implements ClientConnectionManagerFactory {

		public ClientConnectionManager newInstance(HttpParams params, SchemeRegistry schemeRegistry) {
			ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
			return cm;
		}
	}

	public static DefaultHttpClient createClient() {
		return new DefaultHttpClient(createHttpParams());
	}

	public static String readURL(HttpClient client, String url) throws ClientProtocolException, IOException {
		HttpResponse resp = client.execute(new HttpGet(url));
		InputStream is = resp.getEntity().getContent();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buff = new byte[1024];
		int r;
		while ((r = is.read(buff)) != -1)
			bos.write(buff, 0, r);
		is.close();
		bos.close();
		return new String(bos.toByteArray());
	}

	public static void main(String[] args) throws Exception {
		HttpClient client = createClient();
		Map<String, PriceSource> sources = new LinkedHashMap<String, PriceSource>();
		sources.put("M C X", new McxPriceSource("XPM"));
		sources.put("CRYPTSY", new CryptsyPriceSource("XPM"));
		sources.put("COINS-E", new CoinsEPriceSource("XPM"));
		Map<String, Double> prev = new HashMap<String, Double>();

		final Thread mainThred = Thread.currentThread();
		new Thread() {
			public void run() {
				try {
					System.in.read();
				} catch (IOException e) {
					e.printStackTrace();
				}
				mainThred.interrupt();
			};
		}.start();
		while (true) {
			String toSpeak = "";
			for (String name : sources.keySet()) {
				PriceSource ps = sources.get(name);
				String js = readURL(client, ps.getURL());
				Double price = ps.getPrice(js);
				Double round = (double) Math.round(price * 10000);
				if (!round.equals(prev.get(name))) {
					prev.put(name, round);
					toSpeak += name + " " + round.intValue() + " , ";
				}
			}
			if (toSpeak.length() > 0) {
				System.out.println(toSpeak);
				CommandLine cmdLine = CommandLine.parse("espeak '" + toSpeak + "'");
				DefaultExecutor executor = new DefaultExecutor();
				executor.execute(cmdLine);
			}
			Thread.sleep(30 * 1000);
		}
	}
}
