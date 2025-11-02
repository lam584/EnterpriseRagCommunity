// src/services/TopicService.ts
import axios from 'axios';

export interface TopicDTO {
  id?: number;
  name: string;
  description?: string;
}

const BASE_URL = '/api/topics';

export async function fetchCategories(): Promise<TopicDTO[]> {
  const res = await axios.get(`${BASE_URL}`);
  return res.data;
}

export async function createTopic(topic: TopicDTO): Promise<TopicDTO> {
  const res = await axios.post(`${BASE_URL}`, topic);
  return res.data;
}

export async function updateTopic(id: number, topic: Partial<TopicDTO>): Promise<TopicDTO> {
  const res = await axios.put(`${BASE_URL}/${id}`, topic);
  return res.data;
}

export async function deleteTopic(id: number): Promise<void> {
  await axios.delete(`${BASE_URL}/${id}`);
}

