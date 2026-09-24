package com.a501.service.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.a501.service.domain.file.dto.request.CreateUploadRequest;
import com.a501.service.domain.file.dto.request.UploadPurpose;
import com.a501.service.domain.file.repository.StoredFileRepository;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL과 실제 MinIO에 업로드하고, 완료 뒤 발급한 읽기 주소로 같은 바이트를 내려받습니다. */
@Testcontainers
// MOCK 환경에는 ServerContainer가 없어 WebSocketConfig가 기동에 실패하므로 실제 서블릿 컨테이너를 띄웁니다.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FileStorageIntegrationTest {
    private static final String BUCKET = "komit-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    // MinIO가 2026년 9월 quay.io·Docker Hub 공개 이미지를 내려 팀 계정(komit2026)에 사본을 둡니다.
    // 배포(deploy/k3s/base/minio.yaml, deploy/local/docker-compose.yml)와 같은 태그를 씁니다.
    @Container
    static MinIOContainer minio = new MinIOContainer(
            DockerImageName.parse("komit2026/minio:RELEASE.2025-09-07T16-13-09Z")
                    .asCompatibleSubstituteFor("minio/minio"));

    @Autowired
    private FileUploadService fileUploadService;
    @Autowired
    private FileLookupService fileLookupService;
    @Autowired
    private StoredFileRepository storedFileRepository;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.internal-endpoint", minio::getS3URL);
        registry.add("app.storage.public-endpoint", minio::getS3URL);
        registry.add("app.storage.access-key", minio::getUserName);
        registry.add("app.storage.secret-key", minio::getPassword);
        registry.add("app.storage.bucket", () -> BUCKET);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    }

    @BeforeEach
    void clearFiles() {
        storedFileRepository.deleteAll();
    }

    @Test
    void uploadsCompletesAndDownloadsTheSameBytes() throws Exception {
        byte[] original = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
        var target = fileUploadService.start(12, new CreateUploadRequest(
                "portfolio.png",
                "image/png",
                (long) original.length,
                UploadPurpose.PORTFOLIO_IMAGE));

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<Void> upload = http.send(
                HttpRequest.newBuilder(URI.create(target.uploadUrl()))
                        .header("Content-Type", target.headers().get("Content-Type"))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(original))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(upload.statusCode()).isBetween(200, 299);

        var completed = fileUploadService.complete(12, target.uploadId());
        assertThat(completed.fileId()).isEqualTo(target.uploadId());
        assertThat(storedFileRepository.findById(target.uploadId().longValue()))
                .hasValueSatisfying(file -> {
                    assertThat(file.isCompleted()).isTrue();
                    assertThat(file.getStorageKey()).startsWith("files/portfolio-image/12/");
                });

        // 완료 전 PUT 주소가 아직 유효해도 최종 객체에는 영향을 주지 않아야 합니다. (R-FILE-04)
        http.send(
                HttpRequest.newBuilder(URI.create(target.uploadUrl()))
                        .header("Content-Type", "image/png")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[] {1, 2, 3, 4}))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        String readUrl = fileLookupService.findReadUrls(List.of(target.uploadId())).get(target.uploadId());
        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(readUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).containsExactly(original);
    }
}
