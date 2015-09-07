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
    
	private URLConnection urlConnection = null;
	private String strDownloadUrl = null;
	private String strLoginUrl = null;
	private String strLoginUsernameField = null;
	private String strLoginUsername = null;
	private String strLoginPasswordField = null;
	private String strLoginPassword = null;
	private String strLocalFilename = null;
	private String strLocalFilenameSuffix = ".YL";
	private String strDownloadProgressFilename = null;
	private String strDownloadProgressFilenameSuffix = ".cfg";
	private volatile boolean bDownloading = false;			// 这个值是多线程共享的，要处理同步问题
	private boolean bSuccessed = false;			// 下载任务是否成功
	private boolean bStopJob = false;
	private UpdateProgressThread updateProgressThread = null;
	
	private String strOriginalLocalFilename = null;
	private String strMsg = null;
	private boolean bWaitFinished = true;
	private DownloadThread downloadThread = null;		// 主下载线程
	private DownloadFinishedCallback downloadFinishedCallback = null;		// 下载任务完成回调引用
	
	/**
	 * 构造方法，以此方法实例化一个对象时，调用下载启动的start方法会阻塞，下载任务完成之后start方法才能返回
	 * @param strUrl 指定一个用于下载文件的URL，支持断点续传，支持301/302跳转
	 * @param strLocalFilename 指定一个本地文件名，下载下来的文件将会以此文件名保存
	 */
	public Download(String strUrl, String strLocalFilename) {
		this.strDownloadUrl = strUrl;
		this.strLocalFilename = strLocalFilename;
		this.strOriginalLocalFilename = strLocalFilename;
		this.bWaitFinished = true;
	}
	
	/**
	 * 构造方法，使用此方法实例化一个对象，调用下载启动的start方法时会立即返回，下载任务完成之后会执行下载完成回调，因此调用者必须注册回调方法
	 * @param strUrl 指定一个用于下载文件的URL，支持断点续传，支持301/302跳转
	 * @param strLocalFilename 指定一个本地文件名，下载下来的文件将会以此文件名保存
	 * @param bWaitFinished 设置下载等待模式，如果设置为true，表示调用下载的start方法时会阻塞，直到任务完成返回或者失败返回，如果设置为false，表示调用下载的start方法时会立即返回，此时需要调用isDownloadFinished方法检查任务是否已经完成
	 */
	public Download(String strUrl, String strLocalFilename, boolean bWaitFinished) {
		this.strDownloadUrl = strUrl;
		this.strLocalFilename = strLocalFilename;
		this.strOriginalLocalFilename = strLocalFilename;
		this.bWaitFinished = bWaitFinished;
	}
	
	/**
	 * 方法功能：获取登录到下载服务器的URL
	 * @return the LoginUrl 如果之前没有指定一个明确的下载服务器的登录链接，会返回null。 
	 */
	public String getLoginUrl() {
		return strLoginUrl;
	}

	/**
	 * 方法功能：某些下载链接必须要求客户端登录才能下载，因此需要手动的指定登录链接
	 * @param strLoginUrl the strLoginUrl to set 指定登录用的URL，下载时如果需要登录，将会尝试通过这个URL登录到下载服务器。
	 */
	public void setLoginUrl(String strLoginUrl) {
		this.strLoginUrl = strLoginUrl;
	}
	
	/**
	 * 方法功能：设置登录服务器的用户信息，需要同时设置用户名字段名称、用户名、用户密码字段名称、用户密码四项才有可能登录成功。
	 * @param strLoginUsernameField 用户名的字段名称
	 * @param strLoginUsername 用户名
	 * @param strLoginPasswordField 用户密码的字段名称
	 * @param strLoginPassword 用户密码
	 */
	public void setLoginUserInfo(String strLoginUsernameField, String strLoginUsername, String strLoginPasswordField, String strLoginPassword) {
		this.strLoginUsernameField = strLoginUsernameField;
		this.strLoginUsername = strLoginUsername;
		this.strLoginPasswordField = strLoginPasswordField;
		this.strLoginPassword = strLoginPassword;
	}
 	
	/**
	 * 方法功能：注册下载完成回调方法，这里注册的回调方法，是调用者实现的方法，此方法在下载完成时会被调用
	 * @param callBack 回调方法接口对象
	 * 注意：如果在实例化对象时，设置为等待下载完成，则调用此方法没有意义，下载任务完成时也不会执行回调。
	 */
	public void setFinishedCallbackfunc(DownloadFinishedCallback callBack)
    {
		// 对注册回调时传入的回调接口实话方法，要进行严格检查
		if (callBack == null) {
			return ;
		}
		
		if (this.bWaitFinished == false) {
			this.downloadFinishedCallback = callBack;
		}
    }
	
    /**
     * 方法功能：启动下载任务
     * @return 下载成功返回真，下载失败返回假，如果在定义对象时设定了下载等待模式，此处的返回值没有意义，需要检查下载完成回调方法的返回值确认下载是否成功。
     */
	public boolean startJob() {
		
		downloadThread = getDownloadThread();
		if (downloadThread == null) {
			return false;
		}
		
		downloadThread.start();
		// 如果指定要等待下载完成，在这里等待下载线程返回，如果是异步调用，在线程的run方法中执行回调
		if (bWaitFinished == true) {
			try {
				downloadThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return bSuccessed;
	}
	
	/*
	 * 方法功能：获取一个下载线程对象
	 * @return 返回实例化之后的下载线程对象引用
	 */
	private DownloadThread getDownloadThread() { 
        return new DownloadThread(); 
    }
	
	/*
	 * 内部类功能：主下载线程类，执行具体下载文件的任务在此类中完成。
	 * @author liwei
	 *
	 */
	private class DownloadThread extends Thread{

		public void run(){
			bSuccessed = startProc();		// 将下载结果传递出去
			// 如果不等待下载结果完成，也就是异步调用，在下载任务完成之后执行回调
			if (bWaitFinished == false && downloadFinishedCallback != null) {
				downloadFinishedCallback.downloadFinished(bSuccessed, strMsg);
				reset();	// 如果是异步调用的，还需要在这里重置状态和数据
			}
		}
		
		/*
		 * 主下载方法功能：这个方法调用执行时是阻塞的，所以是放在DownloadThread中调用的，
		 * 这样就可以同时实现启动下载后等待完成和启动下载后立即返回然后执行完成回调两种模式。
		 * 这个下载方法只执行下载任务，使用的数据都是外部类的。
		 * @return 返回一个boolean类型，下载成功返回true，下载失败返回false
		 */
		private boolean startProc() {
			boolean bResult = false;
			
			// 参数有效性检查
			if (strDownloadUrl == null ||strDownloadUrl.isEmpty() ||  strLocalFilename == null || strLocalFilename.isEmpty()) {
				System.err.println("Invalid download parameters.");
				return bResult;
			}
			
			// 构建用于下载的本地文件名和下载进度文件名
			if (!buildLocalFilename()) {
				System.err.println("Build download filename failed.");
				return bResult;
			}
			
			System.out.println("Url:" + strDownloadUrl);
			System.out.println("File:" + strLocalFilename);
			
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
					strDownloadProgressFilename, 
					nBytesDownloaded);
	        DownloadProgressManager downloadProgressManager = new DownloadProgressManager(currentDownloadProgressData);
	        
			try {
				boolean bReload = false;
				URL url = new URL(strDownloadUrl);
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
		        	if (currentDownloadProgressData.getUrl().equals(strDownloadUrl) && 
		        			currentDownloadProgressData.getLocalFilename().equals(strLocalFilename) && 
		        			currentDownloadProgressData.getLocalProgressFilename().equals(strDownloadProgressFilename)) {
		        		httpURLConnection.setRequestProperty("Range", "bytes=" + currentDownloadProgressData.getFinishedBytes() + "-");
		        		nBytesDownloaded = currentDownloadProgressData.getFinishedBytes();
		        		nTotalSize += nBytesDownloaded;
		        	} else {
		        		System.err.println("Invalid download progress file, retry download entire file.");
		        		bReload = true;
		        	}
		        } else {
		        	System.out.println("Download progress not found, now will download total file");
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
					RandomAccessFile raf = new RandomAccessFile(strLocalFilename, "rw");  
			        raf.setLength(nRemoteSize); // 预分配文件空间  
			        raf.close();
				}
		        
	            inputStream = urlConnection.getInputStream();
				randomAccessFile = new RandomAccessFile(strLocalFilename, "rw");
				if (nBytesDownloaded > 0) {
					randomAccessFile.seek(nBytesDownloaded);
				}
				
				if (currentDownloadProgressData == null) {
	    			currentDownloadProgressData = new DownloadProgressData(
	    					strDownloadUrl, 
	    					strLocalFilename, 
	    					strDownloadProgressFilename, 
	    					nBytesDownloaded);
	    		} else {
	    			currentDownloadProgressData.setUrl(strDownloadUrl);
	            	currentDownloadProgressData.setLocalFilename(strLocalFilename);
	            	currentDownloadProgressData.setLocalProgressFilename(strDownloadProgressFilename);
	            	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
	    		}
				updateProgressThread = new UpdateProgressThread(nBytesDownloaded);
				
				// 开始下载
	            byte[] buffer = new byte[1024];
	            bDownloading = true;
	            while ((nBytesRead = inputStream.read(buffer)) != -1) {
	                nBytesDownloaded += nBytesRead;
	                
	                if (nBytesRead > 0) {
	                	randomAccessFile.write(buffer, 0, nBytesRead);
	                	
	                	currentDownloadProgressData.setFinishedBytes(nBytesDownloaded);
	                    downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
	                    
	                    if (bStopJob == true) {
	                    	System.out.println("下载任务被中止。");
	                    	break;
	                    }
	                }
	                
	                updateProgressThread.updateDownloadProgress(nBytesDownloaded, nTotalSize);
	            }
	            bDownloading = false;
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
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			} catch (SocketTimeoutException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			} finally {
	            try {
	            	bDownloading = false;
	            	
	            	if (nBytesDownloaded > 0 && nBytesDownloaded < nRemoteSize) {
	            		downloadProgressManager.saveDownloadProgress(currentDownloadProgressData);
	            	} else if (nBytesDownloaded > 0 && nBytesDownloaded == nTotalSize) {
	            		// 如果下载已经完成，删除进度文件，设置正确的文件名
	            		strMsg = "ok";
	            		bResult = true;
	            		System.out.println("下载任务已经结束。");
	            		if (currentDownloadProgressData != null) {
	            			File file = new File(currentDownloadProgressData.getLocalProgressFilename());
	            			if (file.exists()) {
	            				if (!file.delete()) {
	            					Thread.sleep(2000);
	            					file.delete();
	            				}
	            			}
	            			
	            			int nPos = strLocalFilename.indexOf(strLocalFilenameSuffix);
	            			if (nPos != -1) {
	            				String finallyFilename = strLocalFilename.substring(0, nPos);
	            				renameFile(strLocalFilename, finallyFilename);
	            				
	            				// 恢复文件名，使得重复调用此方法下载不会失败
	            				strLocalFilename = strOriginalLocalFilename;
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
					strMsg = e.getMessage();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
					strMsg = e.getMessage();
				}
	        }
			
			// 如果是阻塞模式的享受生活的，下载完成之后立即重置所有下载相关状态和数据
			if (bWaitFinished == true) {
				reset();
			}
			
			return bResult;
		}
	}
	
	/**
	 * 方法功能：停止正在下载的任务，如果下载任务已经完成，调用此方法没有意义。
	 */
	public void stopJob() {
		if (bDownloading == true) {
			this.bStopJob = true;
		}
	}
	
	public boolean startAsLogin() {
		boolean bResult = false;
		
		// 登录才能下载时，需要对相关参加进行更加严格的校验检查
		if (this.strDownloadUrl == null || this.strDownloadUrl.isEmpty() ||
				this.strLocalFilename == null || this.strLocalFilename.isEmpty() || 
				this.strLoginUrl == null || this.strLoginUrl.isEmpty() || 
				strLoginUsernameField == null || strLoginUsernameField.isEmpty() || 
				strLoginUsername == null || strLoginUsername.isEmpty() ||
				strLoginPasswordField == null || strLoginPasswordField.isEmpty() || 
				strLoginPassword == null || strLoginPassword.isEmpty()) {
			System.err.println("Invalid download parameters.");
			return bResult;
		}
		
		if (!buildLocalFilename()) {
			System.err.println("Verify download filename error.");
			return bResult;
		}
		
		if (this.strLoginUrl == null) {
			System.err.println("Login url is invalid, job has aborted.");
			return bResult;
		}
		String strCookieVal = loginToServer();
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
            this.strMsg = "ok";
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			this.strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			bDownloading = false;
			bResult = false;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			this.strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			bDownloading = false;
			bResult = false;
		} catch (SocketTimeoutException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			this.strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			bDownloading = false;
			bResult = false;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			this.strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
			bDownloading = false;
			bResult = false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			this.strMsg = "\"" + e.getClass().getName() + "\" failed on " + e.getMessage();
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
				this.strMsg = e.getMessage();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				this.strMsg = e.getMessage();
			}
        }
		
		return bResult;
	}
	
	/**
	 * 方法功能：获取下载任务消息，此方法可以被外部任意对象调用。
	 * @return the strMsg
	 */
	public String getMessage() {
		return strMsg;
	}

	/*
	 * 方法功能：构建本地文件名，如果本地文件已经存在，会添加序号并进行递增，直到找到一个有效的文件名为止，
	 * 但是如果尝试了9999次仍然没有能够成功构建文件名，会返回false。
	 * 此方法在构建本地下载文件的文件名的同时，还会自动地构建下载进度存储文件的文件名。
	 * @return 构建成功返回true，其他情况返回false
	 */
	private boolean buildLocalFilename() {
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

	/*
	 * 方法功能：使用指定的用户名和密码登录到指定的服务器，某些服务器可能需要登录才能下载，所以需要此方法。
	 * @param strUsername 登录用户名
	 * @param strPwd 登录密码
	 * @return 登录成功返回登录之后的cookie，登录失败返回null。
	 */
	private String loginToServer() {
		
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
	        String strPostLoginInfo = strLoginUsernameField + "=" + strLoginUsername + 
	        		"&" + 
	        		strLoginPasswordField + "=" + strLoginPassword;
	        out.write(strPostLoginInfo); 	//post的关键所在！
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
	 
    /*
     * 方法功能：文件重命名
     * @param oldname 旧文件名，这个指定的文件必须已经存在
     * @param newname 新文件名，这个指定的文件必须不存在
     * @return 命名成功返回true，其他任何情况返回false
     */
    private boolean renameFile(String oldname, String newname) { 
    	boolean bRet = false;
        if(!oldname.equals(newname)){	// 新的文件名和以前文件名不同时,才有必要进行重命名 
            File oldfile = new File(oldname); 
            File newfile = new File(newname); 
            if(!oldfile.exists()){
            	bRet = false;		// 源文件不存在时无法重命名
            }
            if(newfile.exists())	// 新文件名所指定的文件已经存在了，不允许重命名 
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
	
	/*
	 * 静态内部类功能：下载进度数据，这个数据必须被能序列化
	 * @author liwei
	 * @version 1.5
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
	
	/*
	 * 内部类功能：下载进度数据管理器，支持保存下载进度和读取下载进度
	 * @author liwei
	 * @version 2.0
	 */
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
		private synchronized DownloadProgressData readDownloadProgress() {
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
		private synchronized void saveDownloadProgress(DownloadProgressData downloadProgressData) {
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
	
	/*
	 * 内部类功能：下载进度更新线程，在任务启动之前，会自动地初始化一个实例对象，然后根据任务需要不断刷新进度数值。
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
        
        private void updateDownloadProgress(long nFinishedBytes, long nTotalBytes) {
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

	/*
	 * 方法功能：传入一个数值，自动测算后缀的单元字符串，以人类便于阅读的方式进行格式化
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

	/*
	 * 方法功能：重置所有下载相关状态和数据，此方法具有一定的危险性，如果下载任务正在执行，不能调用此方法，会导致线程阻塞死锁
	 */
	private synchronized void reset() {
		urlConnection = null;
		strDownloadUrl = null;
		strLoginUrl = null;
		strLoginUsernameField = null;
		strLoginUsername = null;
		strLoginPasswordField = null;
		strLoginPassword = null;
		strLocalFilename = null;
		strDownloadProgressFilename = null;
		bDownloading = false;			// 这个值是多线程共享的，要处理同步问题
		bSuccessed = false;			// 下载任务是否成功
		bStopJob = false;
		updateProgressThread = null;
		
		strOriginalLocalFilename = null;
		bWaitFinished = true;
		downloadThread = null;		// 主下载线程
		downloadFinishedCallback = null;
	}
}
