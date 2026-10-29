package dev.wuoj.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The contract check itself: correct bodies pass, and wrong or undocumented fields fail. */
class ContractValidatorTest {

    private static boolean valid(String body) {
        var request = SimpleRequest.Builder.get("/api/auth/me").withHeader("Cookie", "SESSION=x").build();
        var response = SimpleResponse.Builder.ok().withContentType("application/json").withBody(body).build();
        return !Contract.VALIDATOR.validate(request, response).hasErrors();
    }

    @Test
    void conformingBodyPasses() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"USER\",\"mustChangePassword\":false}"))
                .isTrue();
    }

    @Test
    void undocumentedFieldFails() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"USER\",\"mustChangePassword\":false,"
                + "\"password\":\"x\"}")).isFalse();
    }

    @Test
    void missingRequiredFieldFails() {
        assertThat(valid("{\"id\":1,\"role\":\"USER\",\"mustChangePassword\":false}")).isFalse();
    }

    @Test
    void wrongEnumValueFails() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"STUDENT\",\"mustChangePassword\":false}"))
                .isFalse();
    }

    /** Answers like {@code saveAdminProblemTests}, whatever it is sent. */
    @RestController
    static class SaveTests {

        @PostMapping(path = "/api/admin/problems/{id}/tests", produces = MediaType.APPLICATION_JSON_VALUE)
        String save(@PathVariable long id) {
            return "{\"changed\":true,\"testSet\":{\"version\":\"v\",\"tests\":[]}}";
        }
    }

    private static final MockMvc SAVE_TESTS = MockMvcBuilders.standaloneSetup(new SaveTests()).build();

    private static void saveTests(String manifest, String... fileParts) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/admin/problems/1/tests");
        request.param("manifest", manifest);
        for (String part : fileParts) request.file(new MockMultipartFile(part, "1.out", null, new byte[] {'x'}));
        SAVE_TESTS.perform(request).andExpect(Contract.conforms());
    }

    @Test
    void multipartFieldsAreCheckedAgainstTheirSchemas() throws Exception {
        saveTests("{\"baseVersion\":\"v\",\"tests\":[{\"input\":{\"keep\":1},\"output\":{\"upload\":0}}]}",
                "files");

        for (String malformed : new String[] {
                "{\"baseVersion\":\"v\"}",
                "{\"baseVersion\":\"v\",\"tests\":[{\"input\":{\"keep\":\"one\"},\"output\":{\"keep\":1}}]}",
                "{\"baseVersion\":\"v\",\"tests\":[{\"input\":{},\"output\":{\"keep\":1}}]}",
                "{\"baseVersion\":\"v\",\"tests\":[{\"input\":{\"keep\":1}}]}",
                "{\"baseVersion\":\"v\",\"tests\":[],\"extra\":1}",
                "not json"}) {
            assertThatThrownBy(() -> saveTests(malformed)).as(malformed).isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Contract violation");
        }
        assertThatThrownBy(() -> saveTests("{\"baseVersion\":\"v\",\"tests\":[{\"input\":{\"keep\":1},"
                + "\"output\":{\"keep\":1}}]}", "attachments"))
                .hasMessageContaining("File part 'attachments' is not in the contract");
    }

    @Test
    void everyOperationOfTheContractIsKnown() {
        assertThat(Contract.operationIds()).contains("login", "logout", "getCurrentUser", "listProblems", "getProblem",
                "listSubmissions", "createSubmission", "getSubmission", "streamSubmissionEvents", "register",
                "getAuthOptions", "changePassword", "getUserProfile", "listAdminUsers", "updateAdminUser",
                "resetUserPassword", "listAuditEntries", "listAdminProblems", "createAdminProblem", "getAdminProblem",
                "updateAdminProblem", "listAdminProblemTests", "saveAdminProblemTests", "downloadAdminProblemTestInput",
                "downloadAdminProblemTestOutput");
    }
}
