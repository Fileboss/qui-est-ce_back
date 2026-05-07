package pack;

import image.ImageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
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

    @Test
    void getOne_returns200_withPackBody() {
        String packId = given()
            .queryParam("packName", "Lookup pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .when().get("/pack/" + packId)
            .then()
                .statusCode(200)
                .body("id", equalTo(packId))
                .body("name", equalTo("Lookup pack"));
    }

    @Test
    void getOne_returns404_whenUnknown() {
        given()
            .when().get("/pack/99999")
            .then().statusCode(404);
    }

    @Test
    void updatePack_renamesPack_andPersistsChange() {
        String packId = given()
            .queryParam("packName", "Original name")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .queryParam("packName", "Renamed")
            .when().patch("/pack/" + packId)
            .then()
                .statusCode(200)
                .body("name", equalTo("Renamed"));

        given()
            .when().get("/pack/" + packId)
            .then()
                .statusCode(200)
                .body("name", equalTo("Renamed"));
    }

    @Test
    void updatePack_returns404_whenUnknown() {
        given()
            .queryParam("packName", "X")
            .when().patch("/pack/99999")
            .then().statusCode(404);
    }

    @Test
    void getAll_includesCreatedPacks() {
        String firstId = given()
            .queryParam("packName", "ListMarker A")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");
        String secondId = given()
            .queryParam("packName", "ListMarker B")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .when().get("/pack")
            .then()
                .statusCode(200)
                .body("id", hasItems(firstId, secondId))
                .body("name", hasItems("ListMarker A", "ListMarker B"));
    }

    @Test
    void getCardsByPack_returnsAssociatedCards() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("cards-key");
        Mockito.when(imageService.getImageUrl("cards-key")).thenReturn("http://fake-bucket/cards-key");

        String packId = given()
            .queryParam("packName", "Cards pack")
            .when().put("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Solo card")
            .multiPart("packId", packId)
            .multiPart("image", "x.png", new byte[]{1, 2, 3}, "image/png")
            .when().put("/card/create")
            .then().statusCode(200);

        given()
            .when().get("/pack/" + packId + "/cards")
            .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", notNullValue())
                .body("[0].name", is("Solo card"))
                .body("[0].imageUrl", is("http://fake-bucket/cards-key"))
                .body("[0].packId", is(packId));
    }
}
