package pack;

import image.ImageService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
@TestSecurity(user = "admin", roles = "admin")
class PackResourceTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x00
    };

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
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Empty pack"))
            .when().post("/pack/create")
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
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Pack with card"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Test Card")
            .multiPart("packId", packId)
            .multiPart("image", "test.png", PNG, "image/png")
            .when().post("/card/create")
            .then().statusCode(200);

        given()
            .when().delete("/pack/" + packId)
            .then().statusCode(204);

        Mockito.verify(imageService).deleteImage("http://fake-bucket/fake-key");
    }

    @Test
    void getOne_returns200_withPackBody() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Lookup pack"))
            .when().post("/pack/create")
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
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Original name"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Renamed"))
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
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "X"))
            .when().patch("/pack/99999")
            .then().statusCode(404);
    }

    @Test
    void getAll_includesCreatedPacks() {
        String firstId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "ListMarker A"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");
        String secondId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "ListMarker B"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .queryParam("max", 100)
            .when().get("/pack")
            .then()
                .statusCode(200)
                .body("first", is(0))
                .body("max", is(100))
                .body("total", greaterThanOrEqualTo(2))
                .body("items.id", hasItems(firstId, secondId))
                .body("items.name", hasItems("ListMarker A", "ListMarker B"));
    }

    @Test
    void getAll_respectsMaxAndExposesTotal() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Slice A"))
            .when().post("/pack/create").then().statusCode(200);
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Slice B"))
            .when().post("/pack/create").then().statusCode(200);
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Slice C"))
            .when().post("/pack/create").then().statusCode(200);

        given()
            .queryParam("first", 0)
            .queryParam("max", 2)
            .when().get("/pack")
            .then()
                .statusCode(200)
                .body("first", is(0))
                .body("max", is(2))
                .body("items.size()", lessThanOrEqualTo(2))
                .body("total", greaterThanOrEqualTo(3));
    }

    @Test
    void getAll_returns400_whenMaxAbove100() {
        given()
            .queryParam("max", 101)
            .when().get("/pack")
            .then().statusCode(400);
    }

    @Test
    void getAll_returns400_whenFirstNegative() {
        given()
            .queryParam("first", -1)
            .when().get("/pack")
            .then().statusCode(400);
    }

    @Test
    void getAll_returns400_whenMaxNegative() {
        given()
            .queryParam("max", -1)
            .when().get("/pack")
            .then().statusCode(400);
    }

    @Test
    void getCardsByPack_returnsAssociatedCards() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("cards-key");
        Mockito.when(imageService.getImageUrl("cards-key")).thenReturn("http://fake-bucket/cards-key");

        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Cards pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .contentType("multipart/form-data")
            .multiPart("name", "Solo card")
            .multiPart("packId", packId)
            .multiPart("image", "x.png", PNG, "image/png")
            .when().post("/card/create")
            .then().statusCode(200);

        given()
            .when().get("/pack/" + packId + "/cards")
            .then()
                .statusCode(200)
                .body("first", is(0))
                .body("max", is(20))
                .body("total", is(1))
                .body("items", hasSize(1))
                .body("items[0].id", notNullValue())
                .body("items[0].name", is("Solo card"))
                .body("items[0].imageUrl", is("http://fake-bucket/cards-key"))
                .body("items[0].packId", is(packId));
    }

    @Test
    void getCardsByPack_paginates() {
        Mockito.when(imageService.uploadImage(any(), anyString())).thenReturn("page-key");
        Mockito.when(imageService.getImageUrl("page-key")).thenReturn("http://fake-bucket/page-key");

        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Paged cards pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        for (int i = 0; i < 3; i++) {
            given()
                .contentType("multipart/form-data")
                .multiPart("name", "Card " + i)
                .multiPart("packId", packId)
                .multiPart("image", "c.png", PNG, "image/png")
                .when().post("/card/create")
                .then().statusCode(200);
        }

        given()
            .queryParam("first", 0)
            .queryParam("max", 2)
            .when().get("/pack/" + packId + "/cards")
            .then()
                .statusCode(200)
                .body("first", is(0))
                .body("max", is(2))
                .body("total", is(3))
                .body("items.size()", lessThanOrEqualTo(2));

        given()
            .queryParam("first", 2)
            .queryParam("max", 2)
            .when().get("/pack/" + packId + "/cards")
            .then()
                .statusCode(200)
                .body("total", is(3))
                .body("items.size()", is(1));
    }

    @Test
    void getCardsByPack_returns400_whenMaxAbove100() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Validation pack"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        given()
            .queryParam("max", 101)
            .when().get("/pack/" + packId + "/cards")
            .then().statusCode(400);
    }

    @Test
    void createPack_returns400_whenPackNameBlank() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", ""))
            .when().post("/pack/create")
            .then().statusCode(400);
    }

    @Test
    void updatePack_returns400_whenPackNameTooLong() {
        String packId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", "Sized"))
            .when().post("/pack/create")
            .then().statusCode(200)
            .extract().jsonPath().getString("id");

        String overLong = "x".repeat(129);
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("packName", overLong))
            .when().patch("/pack/" + packId)
            .then().statusCode(400);
    }
}
