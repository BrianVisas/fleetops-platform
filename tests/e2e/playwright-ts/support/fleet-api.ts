import { randomBytes, randomInt } from "node:crypto";
import { APIRequestContext, expect } from "@playwright/test";

// Test data goes in through the API, so each test sets up exactly the state it
// needs. Requests go to the dashboard's origin, which proxies /api to fleet-api.

// Digits and capitals without I, O and Q: the characters a real VIN may use.
const VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

const pick = (chars: string, length: number) =>
  Array.from({ length }, () => chars[randomInt(chars.length)]).join("");

// A model no other test uses, so a filter on it sees only this test's vehicles
// even though the database is shared and never reset.
export const randomModel = () => "UI-" + pick("ABCDEFGHJKLMNPRSTUVWXYZ0123456789", 8);

export interface Release {
  id: string;
  version: string;
  targetModel: string;
}

export class FleetApi {
  private readonly request: APIRequestContext;

  constructor(request: APIRequestContext) {
    this.request = request;
  }

  /** Registers vehicles of one model and returns their ids, in order. */
  async registerVehicles(model: string, count: number): Promise<string[]> {
    const ids: string[] = [];
    for (let i = 0; i < count; i++) {
      const response = await this.request.post("/api/v1/vehicles", {
        data: { vin: pick(VIN_CHARS, 17), model, swVersion: "1.0.0" },
      });
      await expect(response).toBeOK();
      ids.push((await response.json()).id);
    }
    return ids;
  }

  async publishRelease(model: string): Promise<Release> {
    const response = await this.request.post("/api/v1/releases", {
      data: {
        version: "8." + pick("0123456789", 9),
        targetModel: model,
        checksum: randomBytes(32).toString("hex"),
        sizeBytes: 1024,
      },
    });
    await expect(response).toBeOK();
    return response.json();
  }

  /** Starts a rollout and returns its id. */
  async startRollout(releaseId: string, percentage: number): Promise<string> {
    const response = await this.request.post("/api/v1/rollouts", {
      data: { releaseId, percentage },
    });
    await expect(response).toBeOK();
    return (await response.json()).id;
  }

  /** The vehicle asks for its update, then reports how the installation went. */
  async install(vehicleId: string, version: string, success: boolean) {
    const claim = await this.request.get(`/api/v1/vehicles/${vehicleId}/update`);
    expect(claim.status()).toBe(200);
    const result = await this.request.post(`/api/v1/vehicles/${vehicleId}/update/result`, {
      data: { version, success },
    });
    expect(result.status()).toBe(202);
  }
}
