/**
 * 
 */
package cn.youlin.http;


/**
 * @author liwei
 *
 */
public class DownloadTest implements DownloadFinishedCallback {

	public DownloadFinishedCallback downloadFinishedCallback;  
    
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String strUrl = "http://dldir1.qq.com/music/clntupate/QQMusic_Setup_1174.exe";
		String strLoginUrl = "http://192.168.1.110:8080/j_acegi_security_check";
		String strLocalFilename = "QQMusic_Setup_1174.exe";
		boolean bWaitFinished = false;
		Download download = new Download(strUrl, strLocalFilename, bWaitFinished);
		download.setFinishedCallbackfunc(new DownloadTest());
		boolean bRet = false;
		bRet = download.startJob();
		if (bWaitFinished == true) {
			if (!bRet) {
				System.err.println("Download failed. reson:" + download.getMessage());
			} else {
				System.out.println("Download successed.");
			}
		}		
	}
	
	@Override
	public boolean downloadFinished(boolean status, String strMsg) {
		// TODO Auto-generated method stub
		System.out.println("下载完成回调，status:" + status + "，Message:" + strMsg);
		return status;
	}
}

