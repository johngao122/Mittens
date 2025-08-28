"use client";

import { Edit2, Check, X } from "lucide-react";
import Image from "next/image";
import { useState } from "react";

export default function User() {
  const [projectName, setProjectName] = useState("Project Name");
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(projectName);

  const handleSave = () => {
    setProjectName(editValue);
    setIsEditing(false);
  };

  const handleCancel = () => {
    setEditValue(projectName);
    setIsEditing(false);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSave();
    } else if (e.key === 'Escape') {
      handleCancel();
    }
  };

  return (
    <div className="flex h-16 items-center border-b border-border px-2">
      <div className="flex w-full items-center rounded-md px-2 py-1 hover:bg-slate-200 dark:hover:bg-slate-800">
        <div className="flex items-center">
          <Image
            //src="/avatar.png"
            src=""
            alt="User"
            className="mr-2 rounded-full"
            width={36}
            height={36}
          />
          <div className="flex flex-col">
            <div className="flex items-center gap-1">
              {isEditing ? (
                <>
                  <input
                    type="text"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    onKeyDown={handleKeyPress}
                    className="text-sm font-medium bg-transparent border border-border rounded px-1 py-0.5 w-24"
                    autoFocus
                  />
                  <button
                    onClick={handleSave}
                    className="text-green-600 hover:text-green-700 p-0.5"
                  >
                    <Check size={12} />
                  </button>
                  <button
                    onClick={handleCancel}
                    className="text-red-600 hover:text-red-700 p-0.5"
                  >
                    <X size={12} />
                  </button>
                </>
              ) : (
                <>
                  <span className="text-sm font-medium">{projectName}</span>
                  <button
                    onClick={() => setIsEditing(true)}
                    className="text-muted-foreground hover:text-foreground p-0.5"
                  >
                    <Edit2 size={12} />
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
