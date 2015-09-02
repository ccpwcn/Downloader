/**
 * 
 */
package cn.youlin.http;


/**
 * @author liwei
 *
 */
public class DownloadTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String strUrl = "http://192.168.1.110:8080/job/Youlin_Android_dev/ws/Builds/55/youlin_host-dev.apk";
		String strLoginUrl = "http://192.168.1.110:8080/j_acegi_security_check";
		String strLocalFilename = "QQ7.6.exe";
		String strCentOS = "http://dldir1.qq.com/qqfile/qq/QQ7.6/15742/QQ7.6.exe";
		Download download = new Download(strCentOS, strLocalFilename);
		boolean bRet = false;
		bRet = download.start();
		if (!bRet) {
			System.err.println("Download failed. reson:" + download.getMessage());
		} else {
			System.out.println("Download successed.");
		}
	}
}
