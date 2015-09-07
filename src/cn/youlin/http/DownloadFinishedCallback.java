/**
 * 
 */
package cn.youlin.http;

/**
 * @author liwei
 *
 */
public interface DownloadFinishedCallback {
	/**
	 * 下载完成回调方法
	 * @return 返回boolean类型，返回true表示下载成功，返回false表示下载失败
	 */
	public boolean downloadFinished(boolean status, String strMsg);
}
