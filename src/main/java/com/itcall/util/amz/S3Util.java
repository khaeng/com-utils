package com.itcall.util.amz;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.util.StringUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.itcall.util.ComUtils;
import com.itcall.util.Const;
/**
 * Amazon S3 제어유틸
 * @author khaeng@nate.com
 *
 */
public class S3Util {

	private static S3Util s3Util;
	private AmazonS3 amazonS3;
	private String accessKey;
	private String secretKey;
	private String bucket;
	private String region;

	/**
	 * 기본 환경병수에 셋팅된 정보로 Amazon S3에 접근한다.
	 */
	public S3Util() {
		this(ComUtils.getProperty(Const.ENV_AWS_S3_ACCESS_KEY), ComUtils.getProperty(Const.ENV_AWS_S3_SECRET_KEY), ComUtils.getProperty(Const.ENV_AWS_S3_BUCKET));
	}
	public S3Util(String accessKey, String secretKey, String bucket) {
		this(accessKey, secretKey, bucket, null);
	}
	public S3Util(String accessKey, String secretKey, String bucket, String region) {
		getClient(accessKey, secretKey, bucket, region);
	}

	/**
	 * S3Util을 전역에서 단일하게 사용하기 위한 전역메소드임.
	 * @return
	 */
	public static S3Util S3() {
		if(StringUtils.isEmpty(s3Util)) {
			s3Util = new S3Util();
		}
		return s3Util;
	}

	/**
	 * 기본 연결정보로 연결된 클라이언트를 가져오며, 연결이 비정상적이면, 다시 연결하여 가져온다.
	 * @return
	 */
	public AmazonS3 getClient() {
		return getClient(accessKey, secretKey, bucket, region);
	}

	/**
	 * 지정된 정보로 연결된 클라이언트를 가져오며, 연결이 비정상적이면, 다시 연결하여 가져온다.
	 * @param accessKey
	 * @param secretKey
	 * @param bucket
	 * @param region
	 * @return
	 */
	public AmazonS3 getClient(String accessKey, String secretKey, String bucket, String region) {
		boolean isActive = true;
		try {
			isActive = ( ! StringUtils.isEmpty(amazonS3)
					&& !StringUtils.isEmpty(amazonS3.listBuckets())
					);
		} catch (Exception e) {
			isActive = false;
		}
		if(!isActive) {
			this.accessKey = accessKey;
			this.secretKey = secretKey;
			this.bucket = bucket;
			this.region = region;;
			AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
			AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
					.withCredentials(new AWSStaticCredentialsProvider(credentials));
			if(!StringUtils.isEmpty(region)) {
				amazonS3ClientBuilder.withRegion(region);
			}
			this.amazonS3 = amazonS3ClientBuilder.build();
		}
		return this.amazonS3;
	}

	private String addS3(File file, boolean isPublic) {
		String key = String.format("%s_%d", file.getName() + System.currentTimeMillis());
		getClient().putObject(new PutObjectRequest(bucket, key, file)
				.withCannedAcl(
						isPublic ? CannedAccessControlList.PublicRead : CannedAccessControlList.AuthenticatedRead));
		return key;
	}
	/**
	 * 파일을 Amazon S3로 저장한다. 누구나 접근할 수 있께 저장한다.
	 * @param file
	 * @return
	 */
	public String addS3Public(File file) {
		return addS3(file, true);
	}
	/**
	 * 파일을 Amazon S3로 저장한다. 반환되는 키정보를 알아야 파일에 접근할 수 있거나, 권한이 있어야 URL접근가능한다.
	 * @param file
	 * @return
	 */
	public String addS3(File file) {
		return addS3(file, false);
	}
	/**
	 * 이미 저장한 Amazon S3의 파일의 Key로 외부에서 접근할 수 있는 URL을 가져온다.
	 * @param key
	 * @return
	 */
	public String getS3Url(final String key) {
		return getClient().getUrl(bucket, key).toString();
	}
	/**
	 * 반환받은 키 정보로 Amazon S3에 저장된 파일을 download하여 가져온다.
	 * @param key - 필수.
	 * @param file - 다운로드할 file키를 지정한다. null이면 임시 upload위치에 @key + ".tmp" 파일로 생성된다.
	 * @return
	 * @throws IOException
	 */
	public String getS3(final String key, final File file) throws IOException {
		File downloadFile = file;
		S3Object s3Object = getClient().getObject(this.bucket, key); // getUrl(this.bucket, key);
		InputStream in = s3Object .getObjectContent();
		try {
			if(StringUtils.isEmpty(downloadFile)) {
				String imsiPath = ComUtils.getProperty(Const.DEF_FILE_TEMP_UPLOAD_KEY) + System.getProperty("file.separator", "/") + key;
				downloadFile = Files.createTempFile(imsiPath, Const.DEF_FILE_TEMP_EXT).toFile();
			}
			Files.copy(in, downloadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			in.close();
		}
		return downloadFile.getAbsolutePath();
	}

}
