import unittest
from types import SimpleNamespace

from pydantic import ValidationError

from app.routers import service


def _stages(materials):
    return [(material.fileId is not None, material.accessStage) for material in materials]


class DevelopmentDeliveryMaterialsTest(unittest.TestCase):
    def test_one_deliverable_shows_url_test_and_paid_files_together(self):
        deliverable = service.DeliverableResponse(**service.DELIVERABLES[0])

        # (파일인가, 열람 단계)
        self.assertEqual(_stages(deliverable.materials), [(True, "TEST"), (True, "PAID"), (False, "TEST")])
        ids = [material.deliverableMaterialId for material in deliverable.materials]
        self.assertEqual(len(ids), len(set(ids)))

    def test_a_material_is_either_a_file_or_a_url(self):
        for material in service.DeliverableResponse(**service.DELIVERABLES[0]).materials:
            with self.subTest(material=material.deliverableMaterialId):
                self.assertNotEqual(material.fileId is None, material.url is None)

    def test_response_can_represent_each_resource_combination(self):
        test_file, paid_file, url = service.DELIVERABLES[0]["materials"]
        second_url = {**url, "deliverableMaterialId": 505, "url": "https://staging.example.com"}
        cases = [
            [url],
            [test_file],
            [paid_file],
            [url, paid_file],
            [test_file, paid_file],
            [url, test_file, paid_file],
            [url, second_url, test_file, paid_file],
        ]
        for materials in cases:
            with self.subTest(ids=[material["deliverableMaterialId"] for material in materials]):
                response = service.DeliverableResponse(**{**service.DELIVERABLES[0], "materials": materials})
                self.assertEqual(
                    [material.deliverableMaterialId for material in response.materials],
                    [material["deliverableMaterialId"] for material in materials],
                )

    def test_attaching_file_keeps_urls_and_other_files(self):
        result = service.upload_deliverable(
            401,
            service.AttachDeliverableRequest(fileId=73, accessStage="TEST"),
        )

        self.assertEqual(
            [(material.fileId, material.url) for material in result.materials],
            [(71, None), (72, None), (None, "https://demo.komit.example.com"), (73, None)],
        )

    def test_adding_url_keeps_existing_url_and_files(self):
        result = service.attach_deliverable_url(
            401, service.AttachDeliverableUrlRequest(url="https://new.example.com")
        )

        urls = [material.url for material in result.materials if material.url is not None]
        self.assertEqual(urls, ["https://demo.komit.example.com", "https://new.example.com"])
        self.assertEqual(_stages(result.materials)[:2], [(True, "TEST"), (True, "PAID")])
        self.assertEqual(result.materials[-1].accessStage, "TEST")

    def test_url_must_be_http_or_https_with_host(self):
        for url in ["ftp://demo.example.com", "javascript:alert(1)", "demo.example.com", "https://"]:
            with self.subTest(url=url):
                with self.assertRaises(ValidationError) as raised:
                    service.AttachDeliverableUrlRequest(url=url)
                self.assertEqual(
                    {service._reason(detail) for detail in raised.exception.errors()},
                    {service.MATERIAL_URL_REASON},
                )
        for url in ["http://localhost:3000", "https://demo.example.com/path?q=1"]:
            with self.subTest(url=url):
                self.assertEqual(service.AttachDeliverableUrlRequest(url=url).url, url)

    def test_same_file_same_stage_returns_current_state(self):
        result = service.upload_deliverable(401, service.AttachDeliverableRequest(fileId=71, accessStage="TEST"))

        self.assertEqual(len(result.materials), len(service.DELIVERABLES[0]["materials"]))

    def test_same_file_other_stage_conflicts(self):
        result = service.upload_deliverable(401, service.AttachDeliverableRequest(fileId=72, accessStage="TEST"))

        self.assertEqual(result.status_code, 409)
        self.assertIn(b"MATERIAL_STAGE_CONFLICT", result.body)

    def test_same_url_returns_current_state(self):
        result = service.attach_deliverable_url(
            401, service.AttachDeliverableUrlRequest(url="https://demo.komit.example.com")
        )

        self.assertEqual(len(result.materials), len(service.DELIVERABLES[0]["materials"]))

    def test_registration_is_closed_before_regular_work_ends_and_after_final_request(self):
        file_request = service.AttachDeliverableRequest(fileId=73, accessStage="TEST")
        url_request = service.AttachDeliverableUrlRequest(url="https://new.example.com")
        title_request = service.AddDeliverableRequest(title="추가 설명서")
        for identifier, code in [
            (service.HANDOVER_NOT_OPEN_ID, b"HANDOVER_NOT_OPEN"),
            (service.FINAL_ACCEPTANCE_PASSED_ID, b"FINAL_ACCEPTANCE_PASSED"),
            (service.HANDOVER_UNDER_REVIEW_ID, b"HANDOVER_UNDER_REVIEW"),
        ]:
            for result in [
                service.upload_deliverable(identifier, file_request),
                service.attach_deliverable_url(identifier, url_request),
                service.add_deliverable(identifier, title_request),
            ]:
                with self.subTest(identifier=identifier):
                    self.assertEqual(result.status_code, 409)
                    self.assertIn(code, result.body)

    def test_registration_apis_document_every_failure(self):
        from fastapi import FastAPI

        app = FastAPI()
        app.include_router(service.router)
        paths = app.openapi()["paths"]
        file_responses = paths["/api/deliverables/{deliverableId}/file"]["post"]["responses"]
        url_responses = paths["/api/deliverables/{deliverableId}/urls"]["post"]["responses"]
        add_responses = paths["/api/contracts/{contractId}/deliverables"]["post"]["responses"]

        self.assertTrue({"400", "401", "403", "404", "409", "422"} <= set(file_responses))
        self.assertTrue({"401", "403", "404", "409", "422"} <= set(url_responses))
        self.assertTrue({"401", "403", "404", "409", "422"} <= set(add_responses))
        conflicts = file_responses["409"]["content"]["application/json"]["examples"]
        self.assertEqual(
            {example["value"]["code"] for example in conflicts.values()},
            {
                "CONTRACT_CANCELLED",
                "HANDOVER_NOT_OPEN",
                "FINAL_ACCEPTANCE_PASSED",
                "HANDOVER_UNDER_REVIEW",
                "MATERIAL_STAGE_CONFLICT",
            },
        )

    def test_material_download_does_not_unlock_paid_file(self):
        self.assertEqual(service.download_deliverable_material(501).status_code, 200)
        paid = service.download_deliverable_material(502)
        self.assertEqual(paid.status_code, 409)
        self.assertIn(b"DELIVERABLE_ACCESS_LOCKED", paid.body)
        self.assertEqual(service.download_deliverable_material(506).status_code, 200)

    def test_material_download_documents_every_failure(self):
        from fastapi import FastAPI

        app = FastAPI()
        app.include_router(service.router)
        responses = app.openapi()["paths"]["/api/deliverable-materials/{deliverableMaterialId}/file"]["get"]["responses"]

        self.assertTrue({"200", "401", "404", "409", "422", "500"} <= set(responses))
        self.assertEqual(
            responses["409"]["content"]["application/json"]["example"]["code"], "DELIVERABLE_ACCESS_LOCKED"
        )
        self.assertEqual(
            responses["422"]["content"]["application/json"]["example"]["errors"][0]["field"], "deliverableMaterialId"
        )

    def test_development_completed_means_payout_finished(self):
        description = service.DevelopmentAgreementResponse.model_fields["status"].description

        self.assertIn("COMPLETED는 대금 지급이 끝난 계약이다", description)

    def test_attaching_unattachable_file_reproduces_400(self):
        result = service.upload_deliverable(
            401, service.AttachDeliverableRequest(fileId=service.NOT_ATTACHABLE_FILE_ID, accessStage="TEST")
        )

        self.assertEqual(result.status_code, 400)
        self.assertIn(b"FILE_NOT_ATTACHABLE", result.body)

    def test_url_is_limited_to_2000_characters(self):
        long_url = "https://demo.example.com/" + "a" * (2000 - len("https://demo.example.com/"))
        self.assertEqual(len(service.AttachDeliverableUrlRequest(url=long_url).url), 2000)
        with self.assertRaises(ValueError):
            service.AttachDeliverableUrlRequest(url=long_url + "a")

    def test_url_material_has_no_file_to_download(self):
        self.assertEqual(service.download_deliverable_material(503).status_code, 404)

    def test_legacy_single_file_is_paid_only_for_client(self):
        locked = service.download_deliverable(401)
        self.assertEqual(locked.status_code, 409)
        self.assertIn(b"DELIVERABLE_ACCESS_LOCKED", locked.body)
        self.assertEqual(service.download_deliverable(405).status_code, 200)

    def test_deliverable_upload_rejects_empty_media_type(self):
        request = service.CreateUploadRequest(
            originalName="소스.zip", contentType=";charset=utf-8", sizeBytes=10, purpose="DELIVERABLE"
        )

        result = service.create_upload(request, None)

        self.assertEqual(result.status_code, 400)
        self.assertIn(b"UNSUPPORTED_FILE_TYPE", result.body)

    def test_deliverable_upload_accepts_any_media_type(self):
        request = service.CreateUploadRequest(
            originalName="notes.txt", contentType="text/plain", sizeBytes=10, purpose="DELIVERABLE"
        )

        http = SimpleNamespace(url_for=lambda *_args, **_kwargs: "http://mock/uploads/1/binary")

        self.assertIsInstance(service.create_upload(request, http), service.UploadTargetResponse)

    def test_added_deliverable_is_outside_the_signed_contract(self):
        result = service.add_deliverable(130, service.AddDeliverableRequest(title="추가 설명서"))

        self.assertFalse(result.isContractual)
        self.assertEqual(result.materials, [])

    def test_deliverable_upload_does_not_restrict_media_type(self):
        self.assertIsNone(service.UPLOAD_CONTENT_TYPES["DELIVERABLE"])
        self.assertIn("image/png", service.UPLOAD_CONTENT_TYPES["PROFILE_IMAGE"])


    def test_client_can_download_paid_file_of_completed_contract(self):
        contract = service.get_contract(service.DONE_CONTRACT_ID)
        self.assertEqual(contract.development.status, "COMPLETED")
        materials = [material for deliverable in contract.development.deliverables for material in deliverable.materials]
        paid = [material for material in materials if material.accessStage == "PAID"]
        urls = [material for material in materials if material.url is not None]

        self.assertTrue(paid)
        for material in paid:
            with self.subTest(material=material.deliverableMaterialId):
                self.assertEqual(service.download_deliverable_material(material.deliverableMaterialId).status_code, 200)
        for material in urls:
            with self.subTest(material=material.deliverableMaterialId):
                self.assertEqual(service.download_deliverable_material(material.deliverableMaterialId).status_code, 404)

    def test_completed_contract_materials_do_not_share_ids_with_contract_in_progress(self):
        def material_ids(contract_id):
            contract = service.get_contract(contract_id)
            return {
                material.deliverableMaterialId
                for deliverable in contract.development.deliverables
                for material in deliverable.materials
            }

        self.assertFalse(material_ids(service.DONE_CONTRACT_ID) & material_ids(service.DEV_CONTRACT_ID))

    def test_payout_pending_contract_passed_final_acceptance_but_is_not_completed(self):
        contract = service.get_contract(service.PAYOUT_PENDING_CONTRACT_ID)

        self.assertEqual(contract.contractId, service.PAYOUT_PENDING_CONTRACT_ID)
        self.assertEqual(contract.development.status, "IN_PROGRESS")
        handover = [milestone for milestone in contract.development.milestones if milestone.isHandover]
        self.assertEqual([milestone.status for milestone in handover], ["CONFIRMED"])
        self.assertTrue(all(milestone.status == "CONFIRMED" for milestone in contract.development.milestones))

    def test_client_cannot_download_paid_file_while_payout_pending(self):
        contract = service.get_contract(service.PAYOUT_PENDING_CONTRACT_ID)
        paid = [
            material
            for deliverable in contract.development.deliverables
            for material in deliverable.materials
            if material.accessStage == "PAID"
        ]

        self.assertTrue(paid)
        for material in paid:
            with self.subTest(material=material.deliverableMaterialId):
                result = service.download_deliverable_material(material.deliverableMaterialId)
                self.assertEqual(result.status_code, 409)
                self.assertIn(b"DELIVERABLE_ACCESS_LOCKED", result.body)

    def test_payout_pending_room_leads_to_its_contract(self):
        room = service._room(109)

        self.assertEqual(room["type"], "DEVELOPMENT")
        detail = service._room_detail(room)
        self.assertEqual(detail["contractId"], service.PAYOUT_PENDING_CONTRACT_ID)
        self.assertEqual(service.get_contract(detail["contractId"]).roomId, 109)

if __name__ == "__main__":
    unittest.main()
