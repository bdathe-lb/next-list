export interface HealthPayload {
  service: "nextlist-functions";
  status: "ok";
}

export function createHealthPayload(): HealthPayload {
  return {
    service: "nextlist-functions",
    status: "ok",
  };
}
