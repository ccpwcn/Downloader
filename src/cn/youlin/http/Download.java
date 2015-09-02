package cn.youlin.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * @author liwei
 * @version 1.2
 * @since JDK 1.5
 *
 */
public class Download {
	
	/** 默认编码方式 -UTF8 */  
    // private static final String DEFAULT_ENCODE = "utf-8";
    
	URLConnection urlConnection = null;
	private String strDownloadUrl = null;
	private String strLoginUrl = null;
	private String strLocalFilename = null;
	private String strLocalFilenameSuffix = ".YL";
	private String strDownloadProgressFilename = null;
	private String strDownloadProgressFilenameSuffix = ".cfg";
	private volatile boolean bDownloading = false;			// 这个值是多线程共享的，要处理同步问题
	private UpdateProgressThread updateProgressThread = null;
	
	private String strOriginalLocalFilename = null;
	
	private String strMsg = null;
	
	public Download(String strUrl, String strLocalFilename) {
		this.strDownloadUrl = strUrl;
		this.strLocalFilename = strLocalFilename;
		this.strOriginalLocalFilename = strLocalFilename;
	}
	
	public boolean start() {
		boolean bResult = false;
		if (this.strDownloadUrl == null || this.strLocalFilename == null) {
			System.err.println("Invalid download parameters.");
			return bResult;
		}
		
		if (!verifyDownloadFile()) {
			System.err.println("Verify download filename error.");
			return bResult;
		}
		
		System.out.println("Url:" + this.strDownloadUrl);
		System.out.println("File:" + this.strLocalFilename);
		
		int nHttpResponseStatus = 0;
		long nTotalSize = 0;
		long nRemoteSize = 0;
		long nBytesDownloaded = 0;
        int nBytesRead = 0;
        
        RandomAccessFile randomAccessFile = null;
        InputStream inputStream = null;

        DownloadProgressData currentDownloadProgressData = new DownloadProgressData(
				strDownloadUrl, 
				strLocalFilename, 
				this.strDownloadProgressFilename, 
				nBytesDownloaded);
        DownloadProgressManager downloadProgressManager = new DownloadProgressManager(currentDownloadProgressData);
        
		try {
			boolean bReload = false;
			URL url = new URL(this.strDownloadUrl);
			urlConnection = url.openConnection();
			if (urlConnection == null) {
				System.err.println("Open uri error.");
				return bResult;
			}
			urlConnection.setConnectTimeout(15 * 1000);  
			urlConnection.setReadTimeout(15 * 1000);
			urlConnection.setDoOutput(true);
			
			HttpURLConnection httpURLConnection = (HttpURLConnection)urlConnection;
			httpURLConnection.setUseCaches(false);
			httpURLConnection.setInstanceFollowRedirects(true);		// 明确指示自动跟踪301和302跳转
			
			currentDownloadProgressData = downloadProgressManager.readDownloadProgress();
			
			httpURLConnection.setRequestProperty("Accept-Charset", "utf-8");
	        httpURLConnection.setRequestProperty("Content-Type", "*/*");
	        httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
	        httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
	        if (currentDownloadProgressData != null) {
	        	if (currentDownloadProgressData.getUrl().equals(this.strDownloadUrl) && 
	        			currentDownloadProgressData.getLocalFilename().equals(this.strLocalFilename) && 
	        			currentDownloadProgressData.getLocalProgressFilename().equals(this.strDownloadProgressFilename)) {
	        		httpURLConnection.setRequestProperty("Range", "bytes=" + currentDownloadProgressData.getFinishedBytes() + "-");
	        		nBytesDownloaded = currentDownloadProgressData.getFinishedBytes();
	        		nTotalSize += nBytesDownloaded;
	        	} else {
	        		System.err.println("Invalid download progress file, retry download entire file.");
	        		bReload = true;
	        	}
	        } else {
	        	System.out.println("Download progress not found");
	        	bReload = true;
	        }
			
			nHttpResponseStatus = httpURLConnection.getResponseCode();
			if (nHttpResponseStatus >= 300) {
				System.err.println("Http request failed， response status:" + nHttpResponseStatus);
				return bResult;
			} else if (nHttpResponseStatus == 200) {
				nBytesDownloaded = 0;
			}
			
			String strSize = urlConnection.getHeaderField("Content-Length");
			nRemoteSize = Long.parseLong(strSize);
			if (nRemoteSize <= 0) {
				System.err.println("Get remote file size error.");
				return bResult;
			}
			nTotalSize += nRemoteSize;
			System.out.println("Size:" + nRemoteSize + "---" + getFriendlySize(nRemoteSize));
			
			if (bReload) {
				RandomAccessFile raf = new RandomAccessFile(this.strLocalFilename, "rw");  
		        raf.setLength(nRemoteSize); // 预分配文件空间  
		        raf.close();
			}
	        
            inputStream = urlConnection.getInputStream();
			randomAccessFile = new RandomAccessFile(this.strLocalFilename, "rw");
			if (nBytesDownloaded > 0) {
				randomAccessFile.seek(nBytesDownloaded);
			}
			
			if (currentDownloadProgressData == null) {
    			currentDownloadProgressData = new DownloadProgressData(
    					strDownloadUrl, 
    					strLocalFilename, 
    					this.strDownloadProgressFilename, 
    					nBytesDownloaded);
    		} else {
    			currentDownloadProgressData.setUrl(this.strDownloadUrl);
            	currentDownloadProgressData.setLocalFilename(this.strLocalFilename);
            	currentDownloadProgressData.setLocalProgressFilename(this.strDownloadProgressFilename);
            	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
    		}
			updateProgressThread = new UpdateProgressThread(nBytesDownloaded);
			
            byte[] buffer = new byte[1024];
            bDownloading = true;
            while ((nBytesRead = inputStream.read(buffer)) != -1) {
                nBytesDownloaded += nBytesRead;
                
                if (nBytesRead > 0) {
                	randomAccessFile.write(buffer, 0, nBytesRead);
                	
                	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
                    downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
                }
                
                updateProgressThread.updateDownloadProgress(nBytesDownloaded, nTotalSize);
            }
            bDownloading = false;
            bResult = true;
            try {
            	if (updateProgressThread != null) {
            		updateProgressThread.join();
            	}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				if (updateProgressThread != null) {
					updateProgressThread.interrupt();
				}
			}
            setMessage("ok");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (SocketTimeoutException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} finally {
            try {
            	bDownloading = false;
            	
            	if (nBytesDownloaded > 0 && nBytesDownloaded < nRemoteSize) {
            		downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
            	} else if (nBytesDownloaded > 0 && nBytesDownloaded == nTotalSize) {
            		// 如果下载已经完成，删除进度文件，设置正确的文件名
            		if (currentDownloadProgressData != null) {
            			File file = new File(currentDownloadProgressData.getLocalProgressFilename());
            			if (file.exists()) {
            				file.delete();
            			}
            			
            			int nPos = this.strLocalFilename.indexOf(this.strLocalFilenameSuffix);
            			if (nPos != -1) {
            				String finallyFilename = this.strLocalFilename.substring(0, nPos);
            				renameFile(this.strLocalFilename, finallyFilename);
            				
            				// 恢复文件名，使得重复调用此方法下载不会失败
            				this.strLocalFilename = this.strOriginalLocalFilename;
            			}
            		}
            	}
            	
            	if (inputStream != null) {
            		inputStream.close();
            	}
				if (randomAccessFile != null) {
					randomAccessFile.close();
				}
				
				urlConnection = null;
				
				if (updateProgressThread != null) {
	           		updateProgressThread.join();
	            }
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				setMessage(e.getMessage());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				setMessage(e.getMessage());
			}
        }
		
		return bResult;
	}
	
	public boolean startAsLogin(String strUsername, String strPassword) {
		boolean bResult = false;
		if (this.strDownloadUrl == null || 
				this.strLocalFilename == null || 
				strLoginUrl == null || 
				strUsername == null || 
				strPassword == null) {
			System.err.println("Invalid download parameters.");
			return bResult;
		}
		
		if (!verifyDownloadFile()) {
			System.err.println("Verify download filename error.");
			return bResult;
		}
		
		if (this.strLoginUrl == null) {
			System.err.println("Login url is invalid, job has aborted.");
			return bResult;
		}
		String strCookieVal = loginToServer(strUsername, strPassword);
		if (strCookieVal == null) {
			System.err.println("Attemp login to server failed.");
			return bResult;
		}
		
		System.out.println("Login:success");
		System.out.println("Url:" + this.strDownloadUrl);
		System.out.println("File:" + this.strLocalFilename);
		
		int nHttpResponseStatus = 0;
		long nTotalSize = 0;
		long nRemoteSize = 0;
		long nBytesDownloaded = 0;
        int nBytesRead = 0;
        
        RandomAccessFile randomAccessFile = null;
        InputStream inputStream = null;

        DownloadProgressData currentDownloadProgressData = new DownloadProgressData(
				strDownloadUrl, 
				strLocalFilename, 
				this.strDownloadProgressFilename, 
				nBytesDownloaded);
        DownloadProgressManager downloadProgressManager = new DownloadProgressManager(currentDownloadProgressData);
        
		try {
			boolean bReload = false;
			URL url = new URL(this.strDownloadUrl);
			urlConnection = url.openConnection();
			if (urlConnection == null) {
				System.err.println("Open uri error.");
				return bResult;
			}
			HttpURLConnection httpURLConnection = (HttpURLConnection)urlConnection;
			
			if (strCookieVal != null) {  
                //发送cookie信息上去，以表明自己的身份，否则会被认为没有权限  
				httpURLConnection.setRequestProperty("Cookie", strCookieVal);  
			}
			urlConnection.setConnectTimeout(15 * 1000);  
			urlConnection.setReadTimeout(15 * 1000);
			urlConnection.setDoOutput(true);
			
			httpURLConnection.setUseCaches(false);
			httpURLConnection.setInstanceFollowRedirects(true);		// 明确指示自动跟踪301和302跳转
			
			currentDownloadProgressData = downloadProgressManager.readDownloadProgress();
			
			httpURLConnection.setRequestProperty("Accept-Charset", "utf-8");
	        httpURLConnection.setRequestProperty("Content-Type", "*/*");
	        httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
	        httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
	        if (currentDownloadProgressData != null) {
	        	if (currentDownloadProgressData.getUrl().equals(this.strDownloadUrl) && 
	        			currentDownloadProgressData.getLocalFilename().equals(this.strLocalFilename) && 
	        			currentDownloadProgressData.getLocalProgressFilename().equals(this.strDownloadProgressFilename)) {
	        		httpURLConnection.setRequestProperty("Range", "bytes=" + currentDownloadProgressData.getFinishedBytes() + "-");
	        		nBytesDownloaded = currentDownloadProgressData.getFinishedBytes();
	        		nTotalSize += nBytesDownloaded;
	        	} else {
	        		System.err.println("Invalid download progress file, retry download entire file.");
	        		bReload = true;
	        	}
	        } else {
	        	System.out.println("Download progress not found");
	        	bReload = true;
	        }
	        
	        httpURLConnection.connect();
			
			nHttpResponseStatus = httpURLConnection.getResponseCode();
			if (nHttpResponseStatus >= 300) {
				System.err.println("Http request failed， response status:" + nHttpResponseStatus);
				return bResult;
			} else if (nHttpResponseStatus == 200) {
				nBytesDownloaded = 0;
			}
			
			nRemoteSize = Integer.parseInt(urlConnection.getHeaderField("Content-Length"));
			if (nRemoteSize <= 0) {
				System.err.println("Get remote file size error.");
				return bResult;
			}
			nTotalSize += nRemoteSize;
			System.out.println("Size:" + nRemoteSize + "---" + getFriendlySize(nRemoteSize));
			
			if (bReload) {
				RandomAccessFile raf = new RandomAccessFile(this.strLocalFilename, "rw");  
		        raf.setLength(nRemoteSize); // 预分配文件空间  
		        raf.close();
			}
	        
            inputStream = urlConnection.getInputStream();
			randomAccessFile = new RandomAccessFile(this.strLocalFilename, "rw");
			if (nBytesDownloaded > 0) {
				randomAccessFile.seek(nBytesDownloaded);
			}
			
			if (currentDownloadProgressData == null) {
    			currentDownloadProgressData = new DownloadProgressData(
    					strDownloadUrl, 
    					strLocalFilename, 
    					this.strDownloadProgressFilename, 
    					nBytesDownloaded);
    		} else {
    			currentDownloadProgressData.setUrl(this.strDownloadUrl);
            	currentDownloadProgressData.setLocalFilename(this.strLocalFilename);
            	currentDownloadProgressData.setLocalProgressFilename(this.strDownloadProgressFilename);
            	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
    		}
			updateProgressThread = new UpdateProgressThread(nBytesDownloaded);
			
            byte[] buffer = new byte[1024];
            bDownloading = true;
            while ((nBytesRead = inputStream.read(buffer)) != -1) {
                nBytesDownloaded += nBytesRead;
                
                if (nBytesRead > 0) {
                	randomAccessFile.write(buffer, 0, nBytesRead);
                	
                	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
                    downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
                }
                
                updateProgressThread.updateDownloadProgress(nBytesDownloaded, nTotalSize);
            }
            bDownloading = false;
            bResult = true;
            try {
            	if (updateProgressThread != null) {
            		updateProgressThread.join();
            	}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				if (updateProgressThread != null) {
					updateProgressThread.interrupt();
				}
			}
            setMessage("ok");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (SocketTimeoutException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			setMessage("\"" + e.getClass().getName() + "\" failed on " + e.getMessage());
			bDownloading = false;
			bResult = false;
		} finally {
            try {
            	bDownloading = false;
            	
            	if (nBytesDownloaded > 0 && nBytesDownloaded < nRemoteSize) {
            		downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
            	} else if (nBytesDownloaded > 0 && nBytesDownloaded == nTotalSize) {
            		// 如果下载已经完成，删除进度文件，设置正确的文件名
            		if (currentDownloadProgressData != null) {
            			File file = new File(currentDownloadProgressData.getLocalProgressFilename());
            			if (file.exists()) {
            				file.delete();
            			}
            			
            			int nPos = this.strLocalFilename.indexOf(this.strLocalFilenameSuffix);
            			if (nPos != -1) {
            				String finallyFilename = this.strLocalFilename.substring(0, nPos);
            				renameFile(this.strLocalFilename, finallyFilename);
            				
            				// 恢复文件名，使得重复调用此方法下载不会失败
            				this.strLocalFilename = this.strOriginalLocalFilename;
            			}
            		}
            	}
            	
            	if (inputStream != null) {
            		inputStream.close();
            	}
				if (randomAccessFile != null) {
					randomAccessFile.close();
				}
				
				urlConnection = null;
				
				if (updateProgressThread != null) {
	           		updateProgressThread.join();
	            }
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				setMessage(e.getMessage());
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				setMessage(e.getMessage());
			}
        }
		
		return bResult;
	}
	
	/**
	 * @return the strMsg
	 */
	public String getMessage() {
		return strMsg;
	}

	/**
	 * @param strMsg the strMsg to set
	 */
	public void setMessage(String strMsg) {
		this.strMsg = strMsg;
	}

	private boolean verifyDownloadFile() {
		boolean bRet = false;
		
		int nDotIndex = this.strLocalFilename.lastIndexOf(".");
		String fileMajorName = this.strLocalFilename.substring(0, nDotIndex == -1 ? this.strLocalFilename.length() : nDotIndex);
		String fileExtName = this.strLocalFilename.substring(nDotIndex + 1);
		String currentFilename = null;
		
		File file = new File(this.strLocalFilename);
		if (!file.exists()) {
			bRet = true;
		} else {
			for (int i = 1; i < 10001; i++) {
				currentFilename = fileMajorName + " (" + i + ")." + fileExtName;
				file = new File(currentFilename);
				if (!file.exists()) {
					this.strLocalFilename = currentFilename + this.strLocalFilenameSuffix;
					bRet = true;
					break;
				} else if (i > 9999) {		// 总是找不到适当的文件名，退出不再处理
					this.strLocalFilename = null;
					this.strDownloadProgressFilename = null;
					return false;
				}
			}
		}
		
		// 再处理下载进度文件名
		if (bRet) {
			this.strDownloadProgressFilename = this.strLocalFilename + this.strDownloadProgressFilenameSuffix;
		}
		
		return bRet;
	}
	
	/**
	 * @return the strLocalFilename
	 */
	public String getLocalFilename() {
		return strLocalFilename;
	}

	/**
	 * @param strLocalFilename the strLocalFilename to set
	 */
	public void setLocalFilename(String strLocalFilename) {
		this.strLocalFilename = strLocalFilename;
	}

	/**
	 * @return the strLoginUser
	 */
	public String getLoginUrl() {
		return strLoginUrl;
	}

	/**
	 * @param strLoginUser the strLoginUser to set
	 */
	public void setLoginUrl(String strLoginUrl) {
		this.strLoginUrl = strLoginUrl;
	}

	private String loginToServer(String strUsername, String strPwd) {
		
		String strCookieVal = null;
        BufferedReader reader = null;
        
		try {
			URL url = new URL(this.strLoginUrl);
	        urlConnection = url.openConnection();
	 
	        /**
	         * 然后把连接设为输出模式。URLConnection通常作为输入来使用，比如下载一个Web页。
	         * 通过把URLConnection设为输出，你可以把数据向你个Web页传送。下面是如何做：
	         */
	        urlConnection.setDoOutput(true);
	        /**
	         * 最后，为了得到OutputStream，简单起见，把它约束在Writer并且放入POST信息中，例如： ...
	         */
	        OutputStreamWriter out = new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8");
	        out.write("j_username=admin&j_password=admin"); //post的关键所在！
	        // remember to clean up
	        out.flush();
	        out.close();
	        
	         // 取得cookie，相当于记录了身份，供下次访问时使用  
	         strCookieVal = urlConnection.getHeaderField("Set-Cookie");
	        
	        /**
	         * 这样就可以发送一个看起来象这样的POST：
	         * POST /jobsearch/jobsearch.cgi HTTP 1.0 ACCEPT:
	         * text/plain Content-type: application/x-www-form-urlencoded
	         * Content-length: 99 username=bob password=someword
	         */
	        // 一旦发送成功，用以下方法就可以得到服务器的回应：
//	        String strCurrentLine = null;
//	        String strTotalString = null;
//	        strCurrentLine = "";
//	        strTotalString = "";
//	        InputStream l_urlStream = null;
//	        l_urlStream = urlConnection.getInputStream();
	        // 传说中的三层包装阿！
//	        BufferedReader l_reader = new BufferedReader(new InputStreamReader(l_urlStream));
//	        while ((strCurrentLine = l_reader.readLine()) != null) {
//	            strTotalString += strCurrentLine + "\r\n";
//	        }
//	        System.out.println(strTotalString);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return strCookieVal;
	}
	 
    public boolean renameFile(String oldname, String newname) { 
    	boolean bRet = false;
        if(!oldname.equals(newname)){	// 新的文件名和以前文件名不同时,才有必要进行重命名 
            File oldfile=new File(oldname); 
            File newfile=new File(newname); 
            if(!oldfile.exists()){
            	bRet = false;		// 源文件不存在时无法重命名
            }
            if(newfile.exists())	// 新文件名所指定的文件已经丰在了，不允许重命名 
            	bRet = false; 
            else{ 
                oldfile.renameTo(newfile);
                bRet = true;
            } 
        }else{
        	bRet = false;
        }
        
        return bRet;
    }
	
	/**
	 * Describe 下载进度数据，这个数据必须被能序列化
	 * @author liwei
	 *
	 */
	static private class DownloadProgressData implements Serializable, Cloneable {
		
		private static final long serialVersionUID = 1905090917485243891L;
		
		private String strUrl = null;
		private String strLocalFilename = null;
		private String strLocalProgressFilename = null;
		private long nFinishedBytes = 0;
		
		public DownloadProgressData(String strUrl, String strLocalFilename, String strLocalProgressFilename, long nFinishedBytes) {
			this.strUrl = String.copyValueOf(strUrl.toCharArray());
			this.strLocalFilename = String.copyValueOf(strLocalFilename.toCharArray());
			this.strLocalProgressFilename = String.copyValueOf(strLocalProgressFilename.toCharArray());
			this.nFinishedBytes = nFinishedBytes;
		}
		
		public String getUrl() {
			return strUrl;
		}
		public void setUrl(String strUrl) {
			this.strUrl = strUrl;
		}
		public String getLocalFilename() {
			return strLocalFilename;
		}
		public void setLocalFilename(String strLocalFilename) {
			this.strLocalFilename = strLocalFilename;
		}
		public String getLocalProgressFilename() {
			return strLocalProgressFilename;
		}
		public void setLocalProgressFilename(String strLocalProgressFilename) {
			this.strLocalProgressFilename = strLocalProgressFilename;
		}
		public long getFinishedBytes() {
			return nFinishedBytes;
		}
		public void setFinishedBytes(long nFinishedBytes) {
			this.nFinishedBytes = nFinishedBytes;
		}
		
		protected Object clone() throws CloneNotSupportedException {
			DownloadProgressData data =  (DownloadProgressData) super.clone();
			data.strUrl = String.copyValueOf(strUrl.toCharArray());
			data.strLocalFilename = String.copyValueOf(strLocalFilename.toCharArray());
			data.strLocalProgressFilename = String.copyValueOf(strLocalProgressFilename.toCharArray());
			
			return data;
		}
		
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			String sep = "\n";
			
			sb.append("strUrl:");
			sb.append(strUrl);
			sb.append(sep);
			
			sb.append("strLocalFilename:");
			sb.append(strLocalFilename);
			sb.append(sep);
			
			sb.append("strLocalProgressFilename:");
			sb.append(strLocalProgressFilename);
			sb.append(sep);
			
			sb.append("nFinishedBytes:");
			sb.append(nFinishedBytes);
			
			return sb.toString();
		}
	}
	
	private class DownloadProgressManager {
		
		private DownloadProgressData downloadProgressData = null;
		
		/**
		 * 功能：类DownloadProgressManager的构造方法
		 * @param downloadProgressData 接受一个下载进度数据对象，通过深拷贝的方式保存传入数据，所以调用者的数据不会受到影响
		 */
		public DownloadProgressManager(DownloadProgressData downloadProgressData) {
			try {
				// 执行深拷贝，否则有可能因为对象的多重引用导致数据错误
				this.downloadProgressData = (DownloadProgressData)downloadProgressData.clone();
			} catch (CloneNotSupportedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		/**
		 * 功能：从进度文件中读取下载进度数据，此方法的不会刷新类DownloadProgressManager及其实例中的进度数据
		 * @return 取得的进度数据是一个DownloadProgressData对象的进度数据的引用
		 */
		public DownloadProgressData readDownloadProgress() {
			if (downloadProgressData == null) {
				return null;
			}
			
			File file = new File(this.downloadProgressData.getLocalProgressFilename());   
	        if (!file.exists()) {
	        	return null;
	        }
	        
	        FileInputStream fileInputStream = null;
	        ObjectInputStream objectInputStream = null;
			try {
		        fileInputStream = new FileInputStream(file);
				objectInputStream = new ObjectInputStream(fileInputStream);
				
				this.downloadProgressData = (DownloadProgressData)objectInputStream.readObject();
				if (this.downloadProgressData == null) {
					throw new IOException("读取存储进度失败。");
				}		        
				
 		        // System.out.println("Read progress:\n" + downloadProgressData);

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					if (fileInputStream != null) {
						fileInputStream.close();
					}
					if (objectInputStream != null) {
						objectInputStream.close();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	        
			return this.downloadProgressData;
		}
		
		/**
		 * 功能：保存下载进度数据，为了保证当前实例中的进度数据正确，使用了线程锁，支持多线程调用
		 * @param downloadProgressData 此对象中的数据会被保存到本地文件中，便于下次继续下载时恢复进度
		 */
		public synchronized void saveDownloadProgress(DownloadProgressData downloadProgressData) {
			if (downloadProgressData == null) {
				return ;
			}
			try {
				this.downloadProgressData = (DownloadProgressData)downloadProgressData.clone();
			} catch (CloneNotSupportedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return ;
			}
			
			// 将对象数据保存到文件中
			File file = new File(this.downloadProgressData.getLocalProgressFilename());
			FileOutputStream fileOutputStream = null;
			ObjectOutputStream objectOutputStream = null;
			try {
				fileOutputStream = new FileOutputStream(file);
				objectOutputStream = new ObjectOutputStream(fileOutputStream);
				
				objectOutputStream.writeObject(downloadProgressData);
				// objectOutputStream.writeObject("demo test");
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					if (objectOutputStream != null) {
						objectOutputStream.close();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * 功能说明：传入一个数值，自动测算后缀的单元字符串，以人类便于阅读的方式进行格式化
	 * @param nSize 需要格式化的数值大小
	 * @return String 返回一个字符串，已经包含数值和单位，可以直接使用
	 */
	@SuppressWarnings("unused")
	private String getFriendlySize(long nSize) {
		StringBuffer sb = new StringBuffer();
		final int UNIT = 1024;
		boolean bBitBytes = true;
		if (UNIT == 1000) {
			bBitBytes = false;
		} else {
			bBitBytes = true;
		}
		
		DecimalFormat df=new DecimalFormat("#.00");
		if (nSize < UNIT) {
			sb.append(df.format(1.0f * nSize));
			sb.append(bBitBytes ? "B" : "b");
		} else if (nSize < UNIT * UNIT) {
			sb.append(df.format(1.0f * nSize / UNIT));
			sb.append(bBitBytes ? "KiB" : "KB");
		} else if (nSize < UNIT * UNIT * UNIT) {
			sb.append(df.format(1.0f * nSize / UNIT / UNIT));
			sb.append(bBitBytes ? "MiB" : "MB");
		} else {
			sb.append(df.format(1.0f * nSize / UNIT / UNIT / UNIT));
			sb.append(bBitBytes ? "GiB" : "GB");
		}
		
		return sb.toString();
	}
	
	/**
	 * 功能说明：下载进度更新线程，在任务启动之前，初始化一个实例对象，然后根据任务需要不断刷新进度数值即可。
	 * @author liwei
	 * @version 1.1
	 */
	private class UpdateProgressThread extends Thread {  
       
		private long nFinishedBytes = 0;
		private long nTotalBytes = 0;
		private long nOldSize = 0;
		
        UpdateProgressThread(long nStartBytesSize) {
        	this.nOldSize = nStartBytesSize;
            start();  
        }
        
        public void updateDownloadProgress(long nFinishedBytes, long nTotalBytes) {
    		this.nFinishedBytes = nFinishedBytes;
    		this.nTotalBytes = nTotalBytes;
    	}
  
        public void run() {
        	float nPercent = 0.0f;
        	short nZeroSpeedCount = 0;
        	System.out.printf("STATE               FINISHED          TOTAL    PERCENT          SPEED        REMAINING\n");
            while (bDownloading) {  
                try {  
                    sleep(1000);
                    if (this.nTotalBytes != 0) {
                    	nPercent = 1.0f * this.nFinishedBytes / this.nTotalBytes * 100;
                    }
                    
                    if (this.nFinishedBytes - this.nOldSize == 0) {
                    	nZeroSpeedCount++;
                    } else {
                    	nZeroSpeedCount = 0;
                    }
                    
                    if (nZeroSpeedCount < 3) {
                    	long  ms = 0;
                    	if ((this.nFinishedBytes - this.nOldSize) != 0) {
                    		 ms = (this.nTotalBytes - this.nFinishedBytes) / (this.nFinishedBytes - this.nOldSize) * 1000;	//毫秒数
                    	}
                    	SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");	//初始化Formatter的转换格式。
                    	formatter.setTimeZone(TimeZone.getTimeZone("GMT+00:00"));
                    	String hms = formatter.format(ms);
	                    System.out.printf(
	                    		"downloading  %15s%15s%10.2f%%%15s%17s\n", 
	                    		getFriendlySize(this.nFinishedBytes), 
	                    		getFriendlySize(this.nTotalBytes), 
	                    		nPercent, 
	                    		getFriendlySize(this.nFinishedBytes - this.nOldSize),
	                    		hms);
                    }
                    this.nOldSize = this.nFinishedBytes;
                    if (this.nFinishedBytes >= this.nTotalBytes) {
                    	break;
                    }
                } catch (InterruptedException e) {
                	System.err.println("Detect download progress failed.");
                } finally {
                	if(this.isInterrupted()) {
                		break;
                	}
                }
            }  
        }
    }
}
