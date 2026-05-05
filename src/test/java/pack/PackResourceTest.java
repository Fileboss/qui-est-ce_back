package pack;

import image.ImageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
class PackResourceTest {

    @InjectMock
    ImageService imageService;

    @Test
    void deletePack_whenNotFound_returns404() {
        given()
            .when().delete("/pack/99999")
            .then().statusCode(404);
    }

    @Test
    void deletePack_withNoCards_returns204() {
        String packId = given()
            .queryParam("packName", "Empty pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .when().delete("/pack/" + packId)
            .then().statusCode(204);
    }

    @Test
    void deletePack_withCards_deletesImagesAndReturns204() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("fake-key");
        Mockito.when(imageService.getImageUrl("fake-key")).thenReturn("http://fake-bucket/fake-key");

        String packId = given()
            .queryParam("packName", "Pack with card")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Test Card")
            .multiPart("packId", packId)
            .multiPart("image", "test.png", new byte[]{1, 2, 3}, "image/png")
            .when().put("/card/create")
            .then().statusCode(200);

        given()
            .when().delete("/pack/" + packId)
            .then().statusCode(204);

        Mockito.verify(imageService).deleteImage("http://fake-bucket/fake-key");
    }
}
