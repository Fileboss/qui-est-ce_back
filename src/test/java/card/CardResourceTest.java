package card;

import image.ImageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
class CardResourceTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x00
    };

    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    @InjectMock
    ImageService imageService;

    @Test
    void createCard_returns200_andPersistsCardWithImage() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("upload-key");
        Mockito.when(imageService.getImageUrl("upload-key")).thenReturn("http://fake-bucket/upload-key");

        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Cards target pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Alice")
            .multiPart("packId", packId)
            .multiPart("image", "alice.png", PNG, "image/png")
            .when().post("/card/create")
            .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("name", is("Alice"))
                .body("imageUrl", is("http://fake-bucket/upload-key"))
                .body("packId", equalTo(packId));

        Mockito.verify(imageService).uploadImage(any(), eq("image/png"));
    }

    @Test
    void createCard_returns400_whenImageMissing() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "No-image pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Headless")
            .multiPart("packId", packId)
            .when().post("/card/create")
            .then().statusCode(400);

        Mockito.verify(imageService, Mockito.never()).uploadImage(any(), anyString());
    }

    @Test
    void createCard_returns404_andCompensatesS3_whenPackUnknown() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("orphan-key");
        Mockito.when(imageService.getImageUrl("orphan-key")).thenReturn("http://fake-bucket/orphan-key");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Lost card")
            .multiPart("packId", "99999")
            .multiPart("image", "x.png", PNG, "image/png")
            .when().post("/card/create")
            .then().statusCode(404);

        // The handler validates and uploads first, then persists; if the DB step fails (here:
        // unknown pack), the orphaned S3 object must be compensated by a delete.
        Mockito.verify(imageService).uploadImage(any(), anyString());
        Mockito.verify(imageService).deleteImage("http://fake-bucket/orphan-key");
    }

    @Test
    void deleteCard_returns204_andDeletesImage() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("del-key");
        Mockito.when(imageService.getImageUrl("del-key")).thenReturn("http://fake-bucket/del-key");

        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Delete pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        String cardId = given()
            .contentType("multipart/form-data")
            .multiPart("name", "Doomed")
            .multiPart("packId", packId)
            .multiPart("image", "d.png", PNG, "image/png")
            .when().post("/card/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .when().delete("/card/" + cardId)
            .then().statusCode(204);

        Mockito.verify(imageService).deleteImage("http://fake-bucket/del-key");
    }

    @Test
    void deleteCard_returns404_whenUnknown() {
        given()
            .when().delete("/card/99999")
            .then().statusCode(404);

        Mockito.verify(imageService, Mockito.never()).deleteImage(anyString());
    }

    @Test
    void createCard_returns400_whenContentTypeNotAllowed() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Type-check pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Sneaky")
            .multiPart("packId", packId)
            .multiPart("image", "evil.svg", PNG, "image/svg+xml")
            .when().post("/card/create")
            .then().statusCode(400);

        Mockito.verify(imageService, Mockito.never()).uploadImage(any(), anyString());
    }

    @Test
    void createCard_returns400_whenMagicBytesMismatchDeclaredType() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Mismatch pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Liar")
            .multiPart("packId", packId)
            .multiPart("image", "fake.png", JPEG, "image/png")
            .when().post("/card/create")
            .then().statusCode(400);

        Mockito.verify(imageService, Mockito.never()).uploadImage(any(), anyString());
    }

    @Test
    void createCard_returns400Or413_whenImageTooLarge() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Big pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        byte[] tooBig = new byte[6_000_000];
        // PNG signature in front so a smaller cap (the handler-level one) would otherwise allow it.
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Whale")
            .multiPart("packId", packId)
            .multiPart("image", "big.png", tooBig, "image/png")
            .when().post("/card/create")
            .then().statusCode(anyOf(equalTo(400), equalTo(413)));

        Mockito.verify(imageService, Mockito.never()).uploadImage(any(), anyString());
    }
}
