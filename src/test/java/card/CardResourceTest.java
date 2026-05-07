package card;

import image.ImageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
class CardResourceTest {

    @InjectMock
    ImageService imageService;

    @Test
    void createCard_returns200_andPersistsCardWithImage() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("upload-key");
        Mockito.when(imageService.getImageUrl("upload-key")).thenReturn("http://fake-bucket/upload-key");

        String packId = given()
            .queryParam("packName", "Cards target pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Alice")
            .multiPart("packId", packId)
            .multiPart("image", "alice.png", new byte[]{1, 2, 3}, "image/png")
            .when().put("/card/create")
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
            .queryParam("packName", "No-image pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Headless")
            .multiPart("packId", packId)
            .when().put("/card/create")
            .then().statusCode(400);

        Mockito.verify(imageService, Mockito.never()).uploadImage(any(), anyString());
    }

    @Test
    void createCard_returns404_whenPackUnknown() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("orphan-key");
        Mockito.when(imageService.getImageUrl("orphan-key")).thenReturn("http://fake-bucket/orphan-key");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Lost card")
            .multiPart("packId", "99999")
            .multiPart("image", "x.png", new byte[]{1, 2, 3}, "image/png")
            .when().put("/card/create")
            .then().statusCode(404);

        // Current behavior: image is uploaded before the pack lookup, so it gets orphaned on 404.
        // Encoded here so any future fix that reverses the order is a deliberate, visible change.
        Mockito.verify(imageService).uploadImage(any(), anyString());
        Mockito.verify(imageService, Mockito.never()).deleteImage(anyString());
    }

    @Test
    void deleteCard_returns204_andDeletesImage() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("del-key");
        Mockito.when(imageService.getImageUrl("del-key")).thenReturn("http://fake-bucket/del-key");

        String packId = given()
            .queryParam("packName", "Delete pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        String cardId = given()
            .contentType("multipart/form-data")
            .multiPart("name", "Doomed")
            .multiPart("packId", packId)
            .multiPart("image", "d.png", new byte[]{9, 9}, "image/png")
            .when().put("/card/create")
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
}
